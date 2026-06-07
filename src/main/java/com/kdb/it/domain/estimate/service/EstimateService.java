package com.kdb.it.domain.estimate.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.entity.Bestid;
import com.kdb.it.domain.estimate.entity.Bestim;
import com.kdb.it.domain.estimate.repository.EstimateLineRepository;
import com.kdb.it.domain.estimate.repository.EstimateRepository;
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
 * 소요예산 산정 서비스.
 *
 * <p>상태: 41(작성중) → 42(진행중) → 49(완료). 대상구분 100=정보화사업.</p>
 * <p>쓰기 주체: 작성중=신청자/부서, 진행중 작업=작업자(부서담당자/관리자). 상태 전이는 인접만 허용.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstimateService {

    static final String STS_DRAFT       = "41";
    static final String STS_IN_PROGRESS = "42";
    static final String STS_DONE        = "49";
    static final String TGT_PROJECT     = "100";

    private final EstimateRepository estimateRepository;
    private final EstimateLineRepository lineRepository;
    private final ProjectRepository projectRepository;

    /**
     * 소요예산 산정 신규 신청 생성.
     *
     * @param req  신규 신청 요청 (대상 사업 관리번호, 요청내용)
     * @param user 요청자 인증 정보
     * @return 채번된 문서번호 (REQ-{YYYY}-{4자리})
     * @throws IllegalArgumentException 대상 사업 미존재
     * @throws IllegalStateException    동일 대상에 진행 중(41/42) 문서 존재
     */
    @Transactional
    public String create(EstimateDto.CreateRequest req, CustomUserDetails user) {
        if (!projectRepository.existsByAbusMngNoAndLstYnAndDelYn(req.cncdRfrNo(), "Y", "N")) {
            throw new IllegalArgumentException("대상 사업을 찾을 수 없습니다: " + req.cncdRfrNo());
        }
        if (estimateRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                TGT_PROJECT, req.cncdRfrNo(), List.of(STS_DRAFT, STS_IN_PROGRESS), "N")) {
            throw new IllegalStateException("해당 사업에 진행 중인 소요예산 산정이 이미 있습니다.");
        }
        String docNo = generateDocNo();
        Bestim entity = Bestim.builder()
                .rqmBgReqDocNo(docNo)
                .docVrsSno(1)
                .lstYn("Y")
                .bgPrnTc(TGT_PROJECT)
                .cncdRfrNo(req.cncdRfrNo())
                .stsTc(STS_DRAFT)
                .reqCone(req.reqCone())
                .build();
        estimateRepository.save(entity);
        return docNo;
    }

    /**
     * 마스터 수정 — 작성중(41) 상태에서만 가능.
     *
     * @param docNo 소요예산요청문서번호
     * @param req   수정 요청 (요청내용)
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중이 아닌 경우
     */
    @Transactional
    public void update(String docNo, EstimateDto.UpdateRequest req, CustomUserDetails user) {
        Bestim e = loadCurrent(docNo);
        if (!STS_DRAFT.equals(e.getStsTc())) {
            throw new IllegalStateException("작성중 상태에서만 수정할 수 있습니다.");
        }
        e.updateRequest(req.reqCone());
    }

    /**
     * Soft delete — 작성중(41) 상태에서만 가능.
     *
     * @param docNo 소요예산요청문서번호
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중이 아닌 경우
     */
    @Transactional
    public void delete(String docNo, CustomUserDetails user) {
        Bestim e = loadCurrent(docNo);
        if (!STS_DRAFT.equals(e.getStsTc())) {
            throw new IllegalStateException("작성중 상태에서만 삭제할 수 있습니다.");
        }
        e.delete();
    }

    /**
     * 상태 전이 — 인접 전이만 허용: 41→42, 42→49.
     *
     * @param docNo 소요예산요청문서번호
     * @param req   상태 전이 요청 (목표 상태코드)
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 허용되지 않은 전이인 경우
     */
    @Transactional
    public void changeStatus(String docNo, EstimateDto.StatusRequest req, CustomUserDetails user) {
        Bestim e = loadCurrent(docNo);
        String from = e.getStsTc();
        String to   = req.stsTc();
        boolean allowed = (STS_DRAFT.equals(from) && STS_IN_PROGRESS.equals(to))
                || (STS_IN_PROGRESS.equals(from) && STS_DONE.equals(to));
        if (!allowed) {
            throw new IllegalStateException("허용되지 않은 상태 전이입니다: " + from + " → " + to);
        }
        e.changeStatus(to);
    }

    /**
     * 상세 조회 — 현재 유효 마스터와 명세 목록을 함께 반환.
     *
     * @param docNo 소요예산요청문서번호
     * @return 상세 응답 DTO
     */
    public EstimateDto.Detail get(String docNo) {
        Bestim e = loadCurrent(docNo);
        List<EstimateDto.Line> lines = lineRepository
                .findByRqmBgReqDocNoAndDocVrsSnoAndDelYn(docNo, e.getDocVrsSno(), "N")
                .stream()
                .map(l -> new EstimateDto.Line(l.getSvnTemC(), l.getIoeC(), l.getRqmBgAmt(), l.getOpnnCone()))
                .toList();
        return new EstimateDto.Detail(
                e.getRqmBgReqDocNo(), e.getDocVrsSno(), e.getBgPrnTc(), e.getCncdRfrNo(),
                null, e.getStsTc(), e.getReqCone(), e.getFstEnrUsid(), e.getFstEnrDtm(), lines);
    }

    /**
     * 목록 조회 — 관리자는 전체, 그 외는 소속 부서 한정.
     *
     * @param stsTc     상태구분코드 필터 (null이면 전체)
     * @param cncdRfrNo 사업관리번호 필터 (null이면 전체)
     * @param user      요청자 인증 정보
     * @return 목록 항목 리스트
     */
    public List<EstimateDto.ListItem> list(String stsTc, String cncdRfrNo, CustomUserDetails user) {
        String bbrC = user.isAdmin() ? null : user.getBbrC();
        return estimateRepository.search(stsTc, cncdRfrNo, bbrC);
    }

    /**
     * 팀별 산정 명세 일괄 저장 — 진행중(42) 상태에서만 가능.
     *
     * <p>요청에 포함된 (팀코드+비목코드) 행은 추가/수정하고,
     * 요청에 없는 기존 행은 Soft Delete 처리합니다 (Bitemm 동기화 패턴).</p>
     *
     * @param docNo 소요예산요청문서번호
     * @param req   명세 일괄 저장 요청
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 진행중이 아닌 경우
     */
    @Transactional
    public void saveLines(String docNo, EstimateDto.LinesRequest req, CustomUserDetails user) {
        Bestim e = loadCurrent(docNo);
        if (!STS_IN_PROGRESS.equals(e.getStsTc())) {
            throw new IllegalStateException("진행중 상태에서만 산정 명세를 저장할 수 있습니다.");
        }
        Integer vrs = e.getDocVrsSno();
        List<Bestid> existing = lineRepository.findByRqmBgReqDocNoAndDocVrsSnoAndDelYn(docNo, vrs, "N");

        // 기존 행을 (팀코드|비목코드) 복합 키로 색인
        Map<String, Bestid> byKey = existing.stream()
                .collect(Collectors.toMap(b -> b.getSvnTemC() + "|" + b.getIoeC(), b -> b));

        // 요청 행 처리: 신규 추가 또는 금액/의견 갱신
        Set<String> incomingKeys = new HashSet<>();
        for (EstimateDto.LineRequest line : req.lines()) {
            String key = line.svnTemC() + "|" + line.ioeC();
            incomingKeys.add(key);
            Bestid row = byKey.get(key);
            if (row != null) {
                row.updateEstimate(line.rqmBgAmt(), line.opnnCone());
            } else {
                lineRepository.save(Bestid.builder()
                        .rqmBgReqDocNo(docNo)
                        .docVrsSno(vrs)
                        .svnTemC(line.svnTemC())
                        .ioeC(line.ioeC())
                        .rqmBgAmt(line.rqmBgAmt())
                        .opnnCone(line.opnnCone())
                        .build());
            }
        }

        // 요청에 없는 기존 행 Soft Delete
        for (Bestid row : existing) {
            if (!incomingKeys.contains(row.getSvnTemC() + "|" + row.getIoeC())) {
                row.delete();
            }
        }
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    /**
     * 현재 유효 마스터 조회 (lstYn='Y', delYn='N').
     *
     * @param docNo 소요예산요청문서번호
     * @return 현재 유효 마스터
     * @throws IllegalArgumentException 문서를 찾을 수 없는 경우
     */
    Bestim loadCurrent(String docNo) {
        return estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn(docNo, "Y", "N")
                .orElseThrow(() -> new IllegalArgumentException("소요예산 산정 문서를 찾을 수 없습니다: " + docNo));
    }

    /**
     * 소요예산요청문서번호 채번.
     * 형식: REQ-{연도}-{4자리 시퀀스} (예: REQ-2026-0001)
     */
    private String generateDocNo() {
        long seq = estimateRepository.nextDocSeq();
        return String.format("REQ-%d-%04d", Year.now().getValue(), seq);
    }
}
