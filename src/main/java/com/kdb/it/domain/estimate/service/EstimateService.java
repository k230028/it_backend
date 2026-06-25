package com.kdb.it.domain.estimate.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.entity.Besttm;
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
 * <p>상태: 51(작성중) → 55(진행중) → 59(완료). 대상구분 100=정보화사업.</p>
 * <p>쓰기 주체: 작성중=신청자/부서, 진행중 작업=작업자(부서담당자/관리자). 상태 전이는 인접만 허용.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstimateService {

    static final String STS_DRAFT       = "51";
    static final String STS_IN_PROGRESS = "55";
    static final String STS_DONE        = "59";
    static final String TGT_PROJECT     = "100";

    private final EstimateRepository estimateRepository;
    private final EstimateLineRepository lineRepository;
    private final ProjectRepository projectRepository;
    private final com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;

    /**
     * 소요예산 산정 신규 신청 생성.
     *
     * @param req  신규 신청 요청 (대상 사업 관리번호, 요청내용)
     * @param user 요청자 인증 정보
     * @return 채번된 문서번호 (REQ-{YYYY}-{4자리})
     * @throws IllegalArgumentException 대상 사업 미존재
     * @throws IllegalStateException    동일 대상에 진행 중(51/55) 문서 존재
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
        // 소요예산 산정은 정보화사업(bgPrnTc=100) 전용이므로 조건 없이 적재
        bprojaSyncService.upsert(req.cncdRfrNo(), docNo, STS_DRAFT);
        return docNo;
    }

    /**
     * 마스터 수정 — 작성중(51) 상태에서만 가능.
     *
     * @param docNo 소요예산요청문서번호
     * @param req   수정 요청 (요청내용)
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중이 아닌 경우
     */
    @Transactional
    public void update(String docNo, EstimateDto.UpdateRequest req, CustomUserDetails user) {
        Bestim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc())) {
            throw new IllegalStateException("작성중 상태에서만 수정할 수 있습니다.");
        }
        e.updateRequest(req.reqCone());
    }

    /**
     * Soft delete — 작성중(51) 상태에서만 가능.
     *
     * @param docNo 소요예산요청문서번호
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중이 아닌 경우
     */
    @Transactional
    public void delete(String docNo, CustomUserDetails user) {
        Bestim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc())) {
            throw new IllegalStateException("작성중 상태에서만 삭제할 수 있습니다.");
        }
        e.delete();
        if (TGT_PROJECT.equals(e.getBgPrnTc())) {
            bprojaSyncService.softDelete(e.getCncdRfrNo(), docNo);
        }
    }

    /**
     * 상태 전이 — 인접 전이만 허용: 51→55, 55→59.
     *
     * @param docNo 소요예산요청문서번호
     * @param req   상태 전이 요청 (목표 상태코드)
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 허용되지 않은 전이인 경우
     */
    @Transactional
    public void changeStatus(String docNo, EstimateDto.StatusRequest req, CustomUserDetails user) {
        Bestim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        String from = e.getStsTc();
        String to   = req.stsTc();
        boolean allowed = (STS_DRAFT.equals(from) && STS_IN_PROGRESS.equals(to))
                || (STS_IN_PROGRESS.equals(from) && STS_DONE.equals(to));
        if (!allowed) {
            throw new IllegalStateException("허용되지 않은 상태 전이입니다: " + from + " → " + to);
        }
        e.changeStatus(to);
        if (TGT_PROJECT.equals(e.getBgPrnTc())) {
            bprojaSyncService.upsert(e.getCncdRfrNo(), docNo, to);
        }
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
        // 대상 사업명: 현재 버전 사업을 단건 조회해 채우고, 없으면 null로 둔다.
        String abusNm = projectRepository
                .findByAbusMngNoAndLstYnAndDelYn(e.getCncdRfrNo(), "Y", "N")
                .map(Bprojm::getAbusNm)
                .orElse(null);
        return new EstimateDto.Detail(
                e.getRqmBgReqDocNo(), e.getDocVrsSno(), e.getBgPrnTc(), e.getCncdRfrNo(),
                abusNm, e.getStsTc(), e.getReqCone(), e.getFstEnrUsid(), e.getFstEnrDtm(), lines);
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
     * 팀별 산정 명세 일괄 저장 — 진행중(55) 상태에서만 가능.
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
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_IN_PROGRESS.equals(e.getStsTc())) {
            throw new IllegalStateException("진행중 상태에서만 산정 명세를 저장할 수 있습니다.");
        }
        Integer vrs = e.getDocVrsSno();
        // 삭제여부와 무관하게 모든 행을 조회 — soft-delete된 행도 동일 복합키 충돌 방지를 위해 포함한다.
        List<Besttm> existing = lineRepository.findByRqmBgReqDocNoAndDocVrsSno(docNo, vrs);

        // 기존 행을 (팀코드|비목코드) 복합 키로 색인 (deleted 행 포함)
        Map<String, Besttm> byKey = existing.stream()
                .collect(Collectors.toMap(b -> b.getSvnTemC() + "|" + b.getIoeC(), b -> b));

        // 요청 행 처리: 기존 행이 있으면 (필요 시 복원 후) 갱신, 없으면 신규 INSERT
        Set<String> incomingKeys = new HashSet<>();
        for (EstimateDto.LineRequest line : req.lines()) {
            String key = line.svnTemC() + "|" + line.ioeC();
            incomingKeys.add(key);
            Besttm row = byKey.get(key);
            if (row != null) {
                // soft-delete된 행을 재추가하는 경우: 새 INSERT 대신 복원 후 갱신 (PK 충돌 방지)
                if ("Y".equals(row.getDelYn())) {
                    row.restore();
                }
                row.updateEstimate(line.rqmBgAmt(), line.opnnCone());
            } else {
                lineRepository.save(Besttm.builder()
                        .rqmBgReqDocNo(docNo)
                        .docVrsSno(vrs)
                        .svnTemC(line.svnTemC())
                        .ioeC(line.ioeC())
                        .rqmBgAmt(line.rqmBgAmt())
                        .opnnCone(line.opnnCone())
                        .build());
            }
        }

        // 요청에 없는 활성(delYn='N') 행만 Soft Delete — 이미 삭제된 행은 그대로 둔다.
        for (Besttm row : existing) {
            boolean active = !"Y".equals(row.getDelYn());
            if (active && !incomingKeys.contains(row.getSvnTemC() + "|" + row.getIoeC())) {
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
