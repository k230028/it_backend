package com.kdb.it.domain.payment.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.payment.dto.PaymentDto;
import com.kdb.it.domain.payment.entity.Bpaymm;
import com.kdb.it.domain.payment.entity.Bpaymt;
import com.kdb.it.domain.payment.repository.PaymentLineRepository;
import com.kdb.it.domain.payment.repository.PaymentRepository;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대금지급 서비스. 상태 81→85→89. 대상구분 100=사업/200=전산업무비.
 * 마스터(계약 정보) + 회차별 지급 명세(Bpaymt). 명세 저장은 회차(DFR_TOD) 기준 upsert + soft-deleted 복원.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    static final String STS_DRAFT = "81";
    static final String STS_IN_PROGRESS = "85";
    static final String STS_DONE = "89";
    static final String TGT_PROJECT = "100";
    static final String TGT_COST = "200";

    private final PaymentRepository paymentRepository;
    private final PaymentLineRepository lineRepository;
    private final ProjectRepository projectRepository;
    private final CostRepository costRepository;
    private final com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;

    /**
     * 대금지급 신규 의뢰를 생성합니다.
     *
     * @param req  신규 의뢰 요청 DTO (대상구분, 대상관리번호, 요청내용, 계약명, 계약금액)
     * @param user 요청자 인증 정보
     * @return 생성된 문서관리번호 (예: PAY-2026-0001)
     * @throws IllegalArgumentException 대상 미존재 또는 알 수 없는 대상구분
     * @throws IllegalStateException    동일 대상에 진행 중인 대금지급이 이미 존재
     */
    @Transactional
    public String create(PaymentDto.CreateRequest req, CustomUserDetails user) {
        validateTarget(req.bgPrnTc(), req.cncdRfrNo());
        if (paymentRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                req.bgPrnTc(), req.cncdRfrNo(), List.of(STS_DRAFT, STS_IN_PROGRESS), "N")) {
            throw new IllegalStateException("해당 대상에 진행 중인 대금지급이 이미 있습니다.");
        }
        String docNo = String.format("PAY-%d-%04d", Year.now().getValue(), paymentRepository.nextDocSeq());
        paymentRepository.save(Bpaymm.builder()
                .docMngNo(docNo).docVrsSno(1).lstYn("Y")
                .bgPrnTc(req.bgPrnTc()).cncdRfrNo(req.cncdRfrNo())
                .stsTc(STS_DRAFT).reqCone(req.reqCone()).cttNm(req.cttNm()).cttAmt(req.cttAmt()).build());
        if (TGT_PROJECT.equals(req.bgPrnTc())) {
            bprojaSyncService.upsert(req.cncdRfrNo(), docNo, STS_DRAFT);
        }
        return docNo;
    }

    /**
     * 대상구분(bgPrnTc)에 따라 사업 또는 전산업무비 대상 존재 여부를 검증합니다.
     *
     * @param bgPrnTc   대상구분 (100=사업, 200=전산업무비)
     * @param cncdRfrNo 대상관리번호
     * @throws IllegalArgumentException 알 수 없는 대상구분이거나 대상을 찾을 수 없는 경우
     */
    private void validateTarget(String bgPrnTc, String cncdRfrNo) {
        boolean ok;
        if (TGT_PROJECT.equals(bgPrnTc)) {
            ok = projectRepository.existsByAbusMngNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N");
        } else if (TGT_COST.equals(bgPrnTc)) {
            ok = costRepository.existsByCostBgNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N");
        } else {
            throw new IllegalArgumentException("알 수 없는 대상구분: " + bgPrnTc);
        }
        if (!ok) throw new IllegalArgumentException("대상을 찾을 수 없습니다: " + bgPrnTc + "/" + cncdRfrNo);
    }

    /**
     * 대금지급 마스터를 수정합니다 (작성중 상태에서만 허용).
     *
     * @param docNo 문서관리번호
     * @param req   수정 요청 DTO (요청내용, 계약명, 계약금액)
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중(81) 상태가 아닌 경우
     */
    @Transactional
    public void update(String docNo, PaymentDto.UpdateRequest req, CustomUserDetails user) {
        Bpaymm e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc())) throw new IllegalStateException("작성중 상태에서만 수정할 수 있습니다.");
        e.updateMaster(req.reqCone(), req.cttNm(), req.cttAmt());
    }

    /**
     * 대금지급 문서를 논리 삭제합니다 (작성중 상태에서만 허용).
     *
     * @param docNo 문서관리번호
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중(81) 상태가 아닌 경우
     */
    @Transactional
    public void delete(String docNo, CustomUserDetails user) {
        Bpaymm e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc())) throw new IllegalStateException("작성중 상태에서만 삭제할 수 있습니다.");
        e.delete();
        if (TGT_PROJECT.equals(e.getBgPrnTc())) {
            bprojaSyncService.softDelete(e.getCncdRfrNo(), docNo);
        }
    }

    /**
     * 대금지급 상태를 전이합니다 (허용 전이: 81→85, 85→89).
     *
     * @param docNo 문서관리번호
     * @param req   상태 전이 요청 DTO
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 허용되지 않은 상태 전이인 경우
     */
    @Transactional
    public void changeStatus(String docNo, PaymentDto.StatusRequest req, CustomUserDetails user) {
        Bpaymm e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        String from = e.getStsTc(), to = req.stsTc();
        boolean ok = (STS_DRAFT.equals(from) && STS_IN_PROGRESS.equals(to))
                || (STS_IN_PROGRESS.equals(from) && STS_DONE.equals(to));
        if (!ok) throw new IllegalStateException("허용되지 않은 상태 전이입니다: " + from + " → " + to);
        e.changeStatus(to);
        if (TGT_PROJECT.equals(e.getBgPrnTc())) {
            bprojaSyncService.upsert(e.getCncdRfrNo(), docNo, to);
        }
    }

    /**
     * 회차별 지급 명세를 일괄 저장합니다 (진행중 상태에서만 허용).
     *
     * <p>회차(dfrTod) 기준 upsert: soft-delete된 행은 복원, 존재하지 않으면 신규 생성.
     * 요청에 포함되지 않은 기존 활성 행은 soft-delete 처리합니다.</p>
     *
     * @param docNo 문서관리번호
     * @param req   회차별 지급 명세 일괄 저장 요청 DTO
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 진행중(85) 상태가 아닌 경우
     */
    @Transactional
    public void savePayments(String docNo, PaymentDto.LinesRequest req, CustomUserDetails user) {
        Bpaymm e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_IN_PROGRESS.equals(e.getStsTc())) throw new IllegalStateException("진행중 상태에서만 지급 명세를 저장할 수 있습니다.");
        Integer vrs = e.getDocVrsSno();
        List<Bpaymt> all = lineRepository.findByDocMngNoAndDocVrsSno(docNo, vrs);
        Map<Integer, Bpaymt> byTod = all.stream().collect(Collectors.toMap(Bpaymt::getDfrTod, b -> b));

        Set<Integer> incoming = new HashSet<>();
        for (PaymentDto.LineRequest line : req.lines()) {
            incoming.add(line.dfrTod());
            Bpaymt row = byTod.get(line.dfrTod());
            if (row != null) {
                if ("Y".equals(row.getDelYn())) row.restore();
                row.updatePayment(line.dfrAmt(), line.dfrDt(), line.dfrMplDt(), line.opnnCone());
            } else {
                lineRepository.save(Bpaymt.builder()
                        .docMngNo(docNo).docVrsSno(vrs).dfrTod(line.dfrTod())
                        .dfrAmt(line.dfrAmt()).dfrDt(line.dfrDt()).dfrMplDt(line.dfrMplDt()).opnnCone(line.opnnCone())
                        .build());
            }
        }
        for (Bpaymt row : all) {
            if (!"Y".equals(row.getDelYn()) && !incoming.contains(row.getDfrTod())) row.delete();
        }
    }

    /**
     * 대금지급 상세 정보를 조회합니다.
     *
     * @param docNo 문서관리번호
     * @return 마스터 + 활성 회차별 지급 명세 포함 상세 DTO
     * @throws IllegalArgumentException 문서를 찾을 수 없는 경우
     */
    public PaymentDto.Detail get(String docNo) {
        Bpaymm e = loadCurrent(docNo);
        List<PaymentDto.Line> lines = lineRepository.findByDocMngNoAndDocVrsSnoAndDelYn(docNo, e.getDocVrsSno(), "N")
                .stream().map(l -> new PaymentDto.Line(l.getDfrTod(), l.getDfrAmt(), l.getDfrDt(), l.getDfrMplDt(), l.getOpnnCone()))
                .toList();
        String tgtNm = resolveTargetName(e.getBgPrnTc(), e.getCncdRfrNo());
        return new PaymentDto.Detail(
                e.getDocMngNo(), e.getDocVrsSno(), e.getBgPrnTc(), e.getCncdRfrNo(), tgtNm,
                e.getStsTc(), e.getReqCone(), e.getCttNm(), e.getCttAmt(), e.getFstEnrUsid(), e.getFstEnrDtm(), lines);
    }

    /**
     * 대상구분과 대상관리번호로 대상 명칭을 조회합니다.
     *
     * @param bgPrnTc   대상구분 (100=사업, 200=전산업무비)
     * @param cncdRfrNo 대상관리번호
     * @return 대상 명칭 (없으면 null)
     */
    private String resolveTargetName(String bgPrnTc, String cncdRfrNo) {
        if (TGT_PROJECT.equals(bgPrnTc)) {
            return projectRepository.findByAbusMngNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N").map(p -> p.getAbusNm()).orElse(null);
        }
        if (TGT_COST.equals(bgPrnTc)) {
            return costRepository.findByCostBgNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N").map(c -> c.getCttNm()).orElse(null);
        }
        return null;
    }

    /**
     * 대금지급 목록을 조회합니다. 관리자는 전체 조회, 일반 사용자는 소속 부서 기준으로 필터링합니다.
     *
     * @param stsTc     상태코드 필터 (null이면 전체)
     * @param bgPrnTc   대상구분 필터 (null이면 전체)
     * @param cncdRfrNo 대상관리번호 필터 (null이면 전체)
     * @param user      요청자 인증 정보
     * @return 대금지급 목록 항목 리스트
     */
    public List<PaymentDto.ListItem> list(String stsTc, String bgPrnTc, String cncdRfrNo, CustomUserDetails user) {
        String bbrC = user.isAdmin() ? null : user.getBbrC();
        return paymentRepository.search(stsTc, bgPrnTc, cncdRfrNo, bbrC);
    }

    /**
     * 문서번호로 현재 유효한 대금지급 마스터를 조회합니다 (lstYn='Y', delYn='N').
     *
     * @param docNo 문서관리번호
     * @return 현재 유효 마스터 엔티티
     * @throws IllegalArgumentException 문서를 찾을 수 없는 경우
     */
    Bpaymm loadCurrent(String docNo) {
        return paymentRepository.findByDocMngNoAndLstYnAndDelYn(docNo, "Y", "N")
                .orElseThrow(() -> new IllegalArgumentException("대금지급 문서를 찾을 수 없습니다: " + docNo));
    }
}
