package com.kdb.it.domain.estimate.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.entity.Bestim;
import com.kdb.it.domain.estimate.entity.Besttm;
import com.kdb.it.domain.estimate.repository.EstimateLineRepository;
import com.kdb.it.domain.estimate.repository.EstimateRepository;
import com.kdb.it.infra.eai.config.GweProperties;
import com.kdb.it.infra.eai.dto.EaiRequest;
import com.kdb.it.infra.eai.dto.EaiResult;
import com.kdb.it.infra.eai.dto.GwePayload;
import com.kdb.it.infra.eai.service.EaiService;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소요예산 산정 서비스.
 *
 * <p>상태: 51(작성중) → 55(진행중) → 59(완료). 대상구분 100=정보화사업.
 *
 * <p>쓰기 주체: 작성중=신청자/부서, 진행중 작업=작업자(부서담당자/관리자). 상태 전이는 인접만 허용.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EstimateService {

    static final String STS_DRAFT = "51";
    static final String STS_IN_PROGRESS = "55";
    static final String STS_DONE = "59";
    static final String TGT_PROJECT = "100";

    private final EstimateRepository estimateRepository;
    private final EstimateLineRepository lineRepository;
    private final ProjectRepository projectRepository;
    private final com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;
    private final EaiService eaiService;
    private final GweProperties gweProperties;

    /**
     * 소요예산 산정 신규 신청 생성.
     *
     * @param req 신규 신청 요청 (대상 사업 관리번호, 요청내용)
     * @param user 요청자 인증 정보
     * @return 채번된 문서번호 (REQ-{YYYY}-{4자리})
     * @throws IllegalArgumentException 대상 사업 미존재
     * @throws IllegalStateException 동일 대상에 진행 중(51/55) 문서 존재
     */
    @Transactional
    public String create(EstimateDto.CreateRequest req, CustomUserDetails user) {
        if (!projectRepository.existsByAbusMngNoAndLstYnAndDelYn(req.cncdRfrNo(), "Y", "N")) {
            throw new IllegalArgumentException("대상 사업을 찾을 수 없습니다: " + req.cncdRfrNo());
        }
        if (estimateRepository.existsByCncdRfrNoAndStsTcInAndDelYn(
                req.cncdRfrNo(), List.of(STS_DRAFT, STS_IN_PROGRESS), "N")) {
            throw new IllegalStateException("해당 사업에 진행 중인 소요예산 산정이 이미 있습니다.");
        }
        String docNo = generateDocNo();
        Bestim entity =
                Bestim.builder()
                        .rqmBgReqDocNo(docNo)
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo(req.cncdRfrNo())
                        .stsTc(STS_DRAFT)
                        .reqCone(req.reqCone())
                        .build();
        estimateRepository.save(entity);
        // 소요예산 산정은 정보화사업(ioeC=100) 전용이므로 조건 없이 적재
        bprojaSyncService.upsert(req.cncdRfrNo(), docNo, STS_DRAFT);
        return docNo;
    }

    /**
     * 마스터 수정 — 작성중(51) 상태에서만 가능.
     *
     * @param docNo 소요예산요청문서번호
     * @param req 수정 요청 (요청내용)
     * @param user 요청자 인증 정보
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
     * @param user 요청자 인증 정보
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
        bprojaSyncService.softDelete(e.getCncdRfrNo(), docNo);
    }

    /**
     * 상태 전이 — 인접 전이만 허용: 51→55, 55→59.
     *
     * @param docNo 소요예산요청문서번호
     * @param req 상태 전이 요청 (목표 상태코드)
     * @param user 요청자 인증 정보
     * @throws IllegalStateException 허용되지 않은 전이인 경우
     */
    @Transactional
    public void changeStatus(String docNo, EstimateDto.StatusRequest req, CustomUserDetails user) {
        Bestim e = loadCurrent(docNo);
        OwnershipVerifier.verifyAdmin(user);
        String from = e.getStsTc();
        String to = req.stsTc();
        boolean allowed =
                (STS_DRAFT.equals(from) && STS_IN_PROGRESS.equals(to))
                        || (STS_IN_PROGRESS.equals(from) && STS_DONE.equals(to));
        if (!allowed) {
            throw new IllegalStateException("허용되지 않은 상태 전이입니다: " + from + " → " + to);
        }
        e.changeStatus(to);
        bprojaSyncService.upsert(e.getCncdRfrNo(), docNo, to);
        sendStatusEai("소요예산 산정", docNo, from, to, user);
    }

    /**
     * 상세 조회 — 현재 유효 마스터와 명세 목록을 함께 반환.
     *
     * @param docNo 소요예산요청문서번호
     * @return 상세 응답 DTO
     */
    public EstimateDto.Detail get(String docNo) {
        Bestim e = loadCurrent(docNo);
        List<EstimateDto.Line> lines =
                lineRepository
                        .findByRqmBgReqDocNoAndDocVrsSnoAndDelYn(docNo, e.getDocVrsSno(), "N")
                        .stream()
                        .map(
                                l ->
                                        new EstimateDto.Line(
                                                l.getSvnTemC(),
                                                l.getIoeC(),
                                                l.getRqmBgAmt(),
                                                l.getOpnnCone()))
                        .toList();
        // 대상 사업명: 현재 버전 사업을 단건 조회해 채우고, 없으면 null로 둔다.
        String abusNm =
                projectRepository
                        .findNameViewByAbusMngNoAndLstYnAndDelYn(e.getCncdRfrNo(), "Y", "N")
                        .map(value -> value.getAbusNm())
                        .orElse(null);
        return new EstimateDto.Detail(
                e.getRqmBgReqDocNo(),
                e.getDocVrsSno(),
                TGT_PROJECT,
                e.getCncdRfrNo(),
                abusNm,
                e.getStsTc(),
                e.getReqCone(),
                e.getFstEnrUsid(),
                e.getFstEnrDtm(),
                lines);
    }

    /**
     * 목록 조회 — 관리자는 전체, 그 외는 소속 부서 한정.
     *
     * @param stsTc 상태구분코드 필터 (null이면 전체)
     * @param cncdRfrNo 사업관리번호 필터 (null이면 전체)
     * @param user 요청자 인증 정보
     * @return 목록 항목 리스트
     */
    public List<EstimateDto.ListItem> list(String stsTc, String cncdRfrNo, CustomUserDetails user) {
        String bbrC = user.isAdmin() ? null : user.getBbrC();
        return estimateRepository.search(stsTc, cncdRfrNo, bbrC);
    }

    /**
     * 팀별 산정 명세 일괄 저장 — 진행중(55) 상태에서만 가능.
     *
     * <p>요청에 포함된 (팀코드+비목코드) 행은 추가/수정하고, 요청에 없는 기존 행은 Soft Delete 처리합니다 (Bitemm 동기화 패턴).
     *
     * <p>물리 PK는 (문서번호 + 버전 + 개선의견일련번호)이므로 신규 행에는 문서·버전 안에서 가장 큰 일련번호 다음 값을 부여합니다.
     * 삭제된 행의 번호는 재사용하지 않아 로그 이력과 번호가 어긋나지 않도록 합니다. (담당팀+비목)의 유일성은 더 이상 DB
     * 제약이 아니므로 이 메서드가 기존 행 매칭·갱신으로 계속 보장합니다. 한 번의 호출에 전달된 {@code req.lines()}
     * 안에 동일한 (담당팀+비목) 쌍이 두 번 이상 있어도 물리 행은 하나만 생성·유지되며, 값은 요청 목록에서 더 나중에
     * 나온 행이 최종 반영됩니다(같은 쌍을 두 번의 별도 요청으로 나눠 보낸 것과 동일한 결과).
     *
     * @param docNo 소요예산요청문서번호
     * @param req 명세 일괄 저장 요청
     * @param user 요청자 인증 정보
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
        // 삭제여부와 무관하게 모든 행을 조회 — soft-delete된 행도 (팀+비목) 중복 저장 방지를 위해 포함한다.
        // 개선의견일련번호 오름차순 조회이므로 아래 byKey 병합에서 "낮은 일련번호 행이 대표로 남는다"는
        // 규칙이 스트림 순서(=쿼리 순서)로 실제 보장된다.
        List<Besttm> existing =
                lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc(docNo, vrs);

        // 기존 행을 (팀코드|비목코드) 업무 키로 색인 (deleted 행 포함).
        // 운영 PK는 (문서번호+버전+개선의견일련번호) 3컬럼이라 (팀+비목) 쌍의 유일성을 DB가 더 이상
        // 보장하지 않는다. 동일 키를 가진 물리 행이 2건 이상이어도 merge 함수 없이는
        // Collectors.toMap이 IllegalStateException을 던져 문서 저장 자체가 불가능해지므로,
        // 먼저 나온(=낮은 일련번호) 행을 대표로 남기는 merge 함수를 지정한다. 위 조회가 오름차순을
        // 보장하므로 "먼저 나온" 것이 곧 "일련번호가 낮은" 것과 일치한다.
        Map<String, Besttm> byKey =
                existing.stream()
                        .collect(
                                Collectors.toMap(
                                        b -> b.getSvnTemC() + "|" + b.getIoeC(),
                                        b -> b,
                                        (first, second) -> first));

        // 신규 행 채번 시작값 — 삭제 행을 포함한 최대 일련번호 + 1
        int nextSno =
                existing.stream()
                                .map(Besttm::getIpmOpnnSno)
                                .filter(Objects::nonNull)
                                .mapToInt(Integer::intValue)
                                .max()
                                .orElse(0)
                        + 1;

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
                Besttm created =
                        lineRepository.save(
                                Besttm.builder()
                                        .rqmBgReqDocNo(docNo)
                                        .docVrsSno(vrs)
                                        .ipmOpnnSno(nextSno++)
                                        .svnTemC(line.svnTemC())
                                        .ioeC(line.ioeC())
                                        .rqmBgAmt(line.rqmBgAmt())
                                        .opnnCone(line.opnnCone())
                                        .build());
                // 같은 요청 안에 동일 키가 다시 나오면 새 INSERT 대신 방금 만든 행을 갱신하도록 색인을 즉시 갱신
                byKey.put(key, created);
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
        return estimateRepository
                .findByRqmBgReqDocNoAndLstYnAndDelYn(docNo, "Y", "N")
                .orElseThrow(() -> new IllegalArgumentException("소요예산 산정 문서를 찾을 수 없습니다: " + docNo));
    }

    /** 소요예산요청문서번호 채번. 형식: REQ-{연도}-{4자리 시퀀스} (예: REQ-2026-0001) */
    private String generateDocNo() {
        long seq = estimateRepository.nextDocSeq();
        return String.format("REQ-%d-%04d", Year.now().getValue(), seq);
    }

    private void sendStatusEai(
            String domainName, String docNo, String from, String to, CustomUserDetails user) {
        try {
            EaiResult result =
                    eaiService.sendEai(
                            EaiRequest.gwe(
                                    gweProperties.ifId(),
                                    GwePayload.builder()
                                            .msgGubun("1")
                                            .recvIds(user.getEno())
                                            .subject("[IT Portal] " + domainName + " 상태 변경")
                                            .contents(
                                                    domainName
                                                            + " 문서 "
                                                            + docNo
                                                            + " 상태가 "
                                                            + from
                                                            + "에서 "
                                                            + to
                                                            + "로 변경되었습니다.")
                                            .sendId("systemalert")
                                            .sendName("IT Portal")
                                            .build()));
            if (!result.success() && !result.skipped()) {
                log.warn(
                        "EAI 발송 실패 — 원 업무 처리는 유지합니다. domain={}, docNo={}, 사유={}",
                        domainName,
                        docNo,
                        result.errorMessage());
            }
        } catch (RuntimeException e) {
            log.warn("EAI 발송 실패 — 원 업무 처리는 유지합니다.", e);
        }
    }
}
