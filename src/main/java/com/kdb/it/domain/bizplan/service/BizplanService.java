package com.kdb.it.domain.bizplan.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.bizplan.dto.BizplanDto;
import com.kdb.it.domain.bizplan.entity.Bbizcm;
import com.kdb.it.domain.bizplan.entity.Bbizgm;
import com.kdb.it.domain.bizplan.entity.Bbizpm;
import com.kdb.it.domain.bizplan.entity.Bbizsm;
import com.kdb.it.domain.bizplan.repository.BbizcmRepository;
import com.kdb.it.domain.bizplan.repository.BbizgmRepository;
import com.kdb.it.domain.bizplan.repository.BbizsmRepository;
import com.kdb.it.domain.bizplan.repository.BizplanRepository;
import com.kdb.it.domain.budget.plan.repository.BplanaRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.BprojaId;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업계획 서비스.
 *
 * <p>정보기술부문 계획(BPLANA)에 포함된 사업만 대상이며, 사업과 1:1(PK=ABUS_MNG_NO)이다.
 * 상태(21 작성중 / 29 작성완료)는 BBIZPM이 아니라 BPROJA에
 * {@code (ABUS_MNG_NO, 'BIZ-'+ABUS_MNG_NO)} 행으로 기록한다(A안).</p>
 *
 * <p>권한: 조회·저장·완료 모두 사업 주관부서({@code Bprojm.svnDpmC == user.bbrC}) 또는 ADMIN.
 * 완료(29) 이후에도 반복 저장을 허용하며 상태는 29를 유지한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BizplanService {

    static final String STS_IN_PROGRESS = "21";
    static final String STS_DONE = "29";
    /** 향후일정 기본 시드 일정내용 */
    static final String DEFAULT_SCHEDULE_DSD_CONE = "사업추진";
    /** BPROJA 사업계획 단계 키 접두사 — 협의회가 원본 ABUS_MNG_NO를 키로 쓰므로 충돌 회피 */
    static final String BPROJA_KEY_PREFIX = "BIZ-";
    /** BPROJA 예산편성 단계 키 접두사 (BG_NO 자동 연계용) */
    private static final String BG_KEY_PREFIX = "BG-";

    private final BizplanRepository bizplanRepository;
    private final BbizsmRepository bbizsmRepository;
    private final BbizgmRepository bbizgmRepository;
    private final BbizcmRepository bbizcmRepository;
    private final ProjectRepository projectRepository;
    private final ProjectItemRepository projectItemRepository;
    private final BplanaRepository bplanaRepository;
    private final BprojaRepository bprojaRepository;
    private final BprojaSyncService bprojaSyncService;

    /**
     * 목록 조회 — 관리자는 전체, 그 외는 소속 부서 사업만.
     *
     * @param user 요청자 인증 정보
     * @return BPLANA 포함 사업 기준 목록 (미작성 사업 포함, stsTc=null)
     */
    public List<BizplanDto.ListItem> list(CustomUserDetails user) {
        String bbrC = user.isAdmin() ? null : user.getBbrC();
        return bizplanRepository.search(bbrC);
    }

    /**
     * 상세 진입(create-or-get) — BBIZPM이 없으면 생성하고 BPROJA에 21을 upsert한다.
     *
     * <p>생성 시 사업명은 BPROJM에서 복사하고, 예산번호는 BPROJA의 예산편성 행({@code BG-%})에서
     * 자동 연계한다(없으면 null). 이미 존재하면 부수효과 없이 상세만 반환한다(멱등).</p>
     *
     * @param abusMngNo 사업관리번호
     * @param user      요청자 인증 정보
     * @throws IllegalArgumentException 사업 미존재 또는 BPLANA 미포함
     * @throws AccessDeniedException    주관부서도 관리자도 아닌 경우
     */
    @Transactional
    public BizplanDto.Detail getOrCreate(String abusMngNo, CustomUserDetails user) {
        Bprojm project = loadEligibleProject(abusMngNo);
        verifyDeptOrAdmin(project.getSvnDpmC(), user);

        Bbizpm plan = bizplanRepository.findByAbusMngNoAndDelYn(abusMngNo, "N").orElse(null);
        if (plan == null) {
            plan = Bbizpm.builder()
                    .abusMngNo(abusMngNo)
                    .abusNm(project.getAbusNm())
                    .bgNo(resolveBgNo(abusMngNo))
                    .build();
            bizplanRepository.save(plan);
            bprojaSyncService.upsert(abusMngNo, bprojaKey(abusMngNo), STS_IN_PROGRESS);
        }
        // 사업품목이 한 번도 없으면(신규 생성 또는 아직 미시드된 기존 계획) 예산신청 소요예산 품목을 시드한다.
        // 사용자가 삭제(soft delete)한 이력이 있으면 행이 남으므로 재시드되지 않는다.
        if (bbizgmRepository.findByAbusMngNoOrderBySnoAsc(abusMngNo).isEmpty()) {
            seedItemsFromProject(abusMngNo, plan);
        }
        // 향후일정이 한 번도 없으면 예산신청 사업(BPROJM)의 시작/종료일자로 기본 일정 1건(일정내용='사업추진')을 시드한다.
        if (bbizsmRepository.findByAbusMngNoOrderBySnoAsc(abusMngNo).isEmpty()) {
            seedSchedulesFromProject(abusMngNo, project);
        }
        return toDetail(plan);
    }

    /**
     * 상세 재조회 (부수효과 없음, 진입 이후 refresh 용).
     *
     * @param abusMngNo 사업관리번호
     * @param user      요청자 인증 정보
     * @throws IllegalArgumentException 사업 미존재/BPLANA 미포함/사업계획 미생성
     */
    public BizplanDto.Detail get(String abusMngNo, CustomUserDetails user) {
        Bprojm project = loadEligibleProject(abusMngNo);
        verifyDeptOrAdmin(project.getSvnDpmC(), user);
        return toDetail(loadPlan(abusMngNo));
    }

    /**
     * 전체 저장 — 보고서/전결권 + 일정/품목/계약 병합.
     *
     * <p>자식 행은 (ABUS_MNG_NO, SNO) 기준 upsert: 기존 행은 갱신(삭제행 재사용 시 복원),
     * 미존재 SNO는 INSERT, 요청에 없는 활성 행은 soft delete. SNO는 프론트가 부여한다.
     * 품목 {@code cttSno}는 요청 계약 목록의 SNO만 허용(null 가능).
     * 저장 마지막에 총소요금액을 활성 품목 금액 합계로 재계산한다.
     * 완료(29) 상태에서도 저장 가능하며 상태는 변경하지 않는다.</p>
     *
     * @throws IllegalArgumentException SNO 중복, cttSno 불일치, 사업계획 미생성
     * @throws AccessDeniedException    주관부서도 관리자도 아닌 경우
     */
    @Transactional
    public void save(String abusMngNo, BizplanDto.SaveRequest req, CustomUserDetails user) {
        Bprojm project = loadEligibleProject(abusMngNo);
        verifyDeptOrAdmin(project.getSvnDpmC(), user);
        Bbizpm plan = loadPlan(abusMngNo);

        String sanitized = req.redtConeInf() == null ? null : HtmlSanitizer.sanitize(req.redtConeInf());
        plan.updateBasics(sanitized, req.itPtlEdrtTc());

        Set<Integer> contractSnos = mergeContracts(abusMngNo, req.contracts());
        BigDecimal total = mergeItems(abusMngNo, req.items(), contractSnos);
        mergeSchedules(abusMngNo, req.schedules());

        plan.changeTotalAmount(total);
    }

    /**
     * 완료 처리 — BPROJA 상태 21→29 전이만 허용.
     *
     * @throws IllegalArgumentException 목표 상태가 29가 아닌 경우
     * @throws IllegalStateException    현재 상태가 21이 아닌 경우
     */
    @Transactional
    public void complete(String abusMngNo, BizplanDto.StatusRequest req, CustomUserDetails user) {
        Bprojm project = loadEligibleProject(abusMngNo);
        verifyDeptOrAdmin(project.getSvnDpmC(), user);
        loadPlan(abusMngNo);

        if (!STS_DONE.equals(req.stsTc())) {
            throw new IllegalArgumentException("사업계획은 완료(29) 전이만 허용됩니다: " + req.stsTc());
        }
        String current = currentStatus(abusMngNo);
        if (!STS_IN_PROGRESS.equals(current)) {
            throw new IllegalStateException("작성중(21) 상태에서만 완료할 수 있습니다. 현재: " + current);
        }
        bprojaSyncService.upsert(abusMngNo, bprojaKey(abusMngNo), STS_DONE);
    }

    // ----- 내부 헬퍼 -----

    /**
     * 해당 사업(BPROJM)의 예산신청 소요예산 상세내용 품목
     * (BITEMM 최신·유효본, {@code LST_YN='Y'} · {@code DEL_YN='N'})을 사업품목(BBIZGM)으로 복사한다.
     * SNO는 1부터 순번 부여하고, 총소요금액은 복사한 품목 금액(amt)의 합계로 설정한다.
     *
     * <p>호출부(getOrCreate)에서 사업품목 행이 하나도 없을 때만 호출하므로, 사용자가 편집·삭제한
     * 사업품목을 덮어쓰지 않는다(삭제는 soft delete라 행이 남아 재시드되지 않음).
     * 원본 품목이 없으면 아무 것도 하지 않는다.</p>
     *
     * @param abusMngNo 사업관리번호(= BITEMM.ABUS_MNG_NO)
     * @param plan      대상 사업계획(총소요금액 설정 대상)
     */
    private void seedItemsFromProject(String abusMngNo, Bbizpm plan) {
        List<Bitemm> sourceItems = projectItemRepository
                .findByAbusMngNoAndDelYnAndLstYn(abusMngNo, "N", "Y").stream()
                .sorted(Comparator
                        .comparing(Bitemm::getGclMngNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Bitemm::getSno, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (sourceItems.isEmpty()) {
            return;
        }
        int sno = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (Bitemm src : sourceItems) {
            bbizgmRepository.save(Bbizgm.builder()
                    .abusMngNo(abusMngNo)
                    .sno(sno++)
                    .gclNm(src.getGclNm())
                    .ioeC(src.getIoeC())
                    .qty(src.getQty() == null ? null : src.getQty().longValue())
                    .amt(src.getAmt())
                    .fcAmt(src.getFcAmt())
                    .curC(src.getCurC())
                    .xcr(src.getXcr())
                    .xcrBseDt(src.getXcrBseDt())
                    .build());
            if (src.getAmt() != null) {
                total = total.add(src.getAmt());
            }
        }
        plan.changeTotalAmount(total);
    }

    /**
     * 향후일정이 한 번도 없을 때 — 예산신청 사업(BPROJM)의 시작/종료일자로 기본 일정 1건을 시드한다.
     * 일정내용 기본값은 '사업추진'이며, 사업 일자가 없으면 해당 일자는 비워 둔다.
     *
     * <p>호출부(getOrCreate)에서 사업일정 행이 하나도 없을 때만 호출하므로, 사용자가 편집·삭제한
     * 일정을 덮어쓰지 않는다(삭제는 soft delete라 행이 남아 재시드되지 않음).</p>
     *
     * @param abusMngNo 사업관리번호
     * @param project   대상 사업(시작/종료일자 원본)
     */
    private void seedSchedulesFromProject(String abusMngNo, Bprojm project) {
        bbizsmRepository.save(Bbizsm.builder()
                .abusMngNo(abusMngNo)
                .sno(1)
                .dsdCone(DEFAULT_SCHEDULE_DSD_CONE)
                .sttDt(toYmd(project.getSttDtm()))
                .endDt(toYmd(project.getEndDtm()))
                .build());
    }

    /** LocalDate → YYYYMMDD 문자열 (null이면 null). */
    private static String toYmd(LocalDate date) {
        return date == null ? null : date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String bprojaKey(String abusMngNo) {
        return BPROJA_KEY_PREFIX + abusMngNo;
    }

    private Bprojm loadEligibleProject(String abusMngNo) {
        Bprojm project = projectRepository.findByAbusMngNoAndLstYnAndDelYn(abusMngNo, "Y", "N")
                .orElseThrow(() -> new IllegalArgumentException("사업을 찾을 수 없습니다: " + abusMngNo));
        if (!bplanaRepository.existsByPrjMngNoAndDelYn(abusMngNo, "N")) {
            throw new IllegalArgumentException("정보기술부문 계획에 포함되지 않은 사업입니다: " + abusMngNo);
        }
        return project;
    }

    private Bbizpm loadPlan(String abusMngNo) {
        return bizplanRepository.findByAbusMngNoAndDelYn(abusMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("사업계획이 아직 생성되지 않았습니다: " + abusMngNo));
    }

    private void verifyDeptOrAdmin(String svnDpmC, CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        if (user.isAdmin()) {
            return;
        }
        if (svnDpmC != null && svnDpmC.equals(user.getBbrC())) {
            return;
        }
        throw new AccessDeniedException("사업 주관부서 또는 관리자만 수행할 수 있습니다.");
    }

    /**
     * BPROJA 예산편성 행(BG-%)에서 예산번호 자동 연계 (없으면 null).
     *
     * <p>한 사업의 예산편성 단계 BPROJA 행은 1건이라는 전제로 첫 BG- 키를 사용한다.
     * 다건이 존재할 경우 어느 행이 선택될지는 보장되지 않는다.</p>
     */
    private String resolveBgNo(String abusMngNo) {
        return bprojaRepository.findByAbusMngNoAndDelYn(abusMngNo, "N").stream()
                .map(Bproja::getCncdRfrNo)
                .filter(key -> key != null && key.startsWith(BG_KEY_PREFIX))
                .findFirst()
                .orElse(null);
    }

    private String currentStatus(String abusMngNo) {
        return bprojaRepository.findById(new BprojaId(abusMngNo, bprojaKey(abusMngNo)))
                .filter(a -> !"Y".equals(a.getDelYn()))
                .map(Bproja::getStsTc)
                .orElse(STS_IN_PROGRESS);
    }

    private static void verifyUniqueSnos(String label, List<Integer> snos) {
        Set<Integer> seen = new HashSet<>();
        for (Integer sno : snos) {
            if (!seen.add(sno)) {
                throw new IllegalArgumentException(label + " 일련번호가 중복되었습니다: " + sno);
            }
        }
    }

    private void mergeSchedules(String abusMngNo, List<BizplanDto.ScheduleRequest> rows) {
        verifyUniqueSnos("일정", rows.stream().map(BizplanDto.ScheduleRequest::sno).toList());
        Map<Integer, Bbizsm> bySno = bbizsmRepository.findByAbusMngNoOrderBySnoAsc(abusMngNo).stream()
                .collect(Collectors.toMap(Bbizsm::getSno, Function.identity()));
        Set<Integer> incoming = new HashSet<>();
        for (BizplanDto.ScheduleRequest row : rows) {
            incoming.add(row.sno());
            Bbizsm existing = bySno.get(row.sno());
            if (existing != null) {
                if ("Y".equals(existing.getDelYn())) {
                    existing.restore();
                }
                existing.updateSchedule(row.dsdCone(), row.sttDt(), row.endDt());
            } else {
                bbizsmRepository.save(Bbizsm.builder()
                        .abusMngNo(abusMngNo).sno(row.sno())
                        .dsdCone(row.dsdCone()).sttDt(row.sttDt()).endDt(row.endDt())
                        .build());
            }
        }
        softDeleteMissing(bySno.values(), Bbizsm::getSno, incoming, Bbizsm::delete);
    }

    private BigDecimal mergeItems(String abusMngNo, List<BizplanDto.ItemRequest> rows,
            Set<Integer> contractSnos) {
        verifyUniqueSnos("품목", rows.stream().map(BizplanDto.ItemRequest::sno).toList());
        for (BizplanDto.ItemRequest row : rows) {
            if (row.cttSno() != null && !contractSnos.contains(row.cttSno())) {
                throw new IllegalArgumentException(
                        "품목이 참조하는 계약 일련번호가 존재하지 않습니다: " + row.cttSno());
            }
        }
        Map<Integer, Bbizgm> bySno = bbizgmRepository.findByAbusMngNoOrderBySnoAsc(abusMngNo).stream()
                .collect(Collectors.toMap(Bbizgm::getSno, Function.identity()));
        Set<Integer> incoming = new HashSet<>();
        for (BizplanDto.ItemRequest row : rows) {
            incoming.add(row.sno());
            Bbizgm existing = bySno.get(row.sno());
            if (existing != null) {
                if ("Y".equals(existing.getDelYn())) {
                    existing.restore();
                }
                existing.updateItem(row.gclNm(), row.ioeC(), row.qty(), row.amt(), row.fcAmt(),
                        row.curC(), row.xcr(), row.xcrBseDt(), row.cttSno());
            } else {
                bbizgmRepository.save(Bbizgm.builder()
                        .abusMngNo(abusMngNo).sno(row.sno())
                        .gclNm(row.gclNm()).ioeC(row.ioeC()).qty(row.qty())
                        .amt(row.amt()).fcAmt(row.fcAmt()).curC(row.curC())
                        .xcr(row.xcr()).xcrBseDt(row.xcrBseDt()).cttSno(row.cttSno())
                        .build());
            }
        }
        softDeleteMissing(bySno.values(), Bbizgm::getSno, incoming, Bbizgm::delete);
        // 총소요금액 = 요청(=저장 후 활성) 품목 금액 합계 (BITEMM 규칙과 동일하게 amt는 KRW 환산값)
        return rows.stream()
                .map(BizplanDto.ItemRequest::amt)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Set<Integer> mergeContracts(String abusMngNo, List<BizplanDto.ContractRequest> rows) {
        verifyUniqueSnos("계약", rows.stream().map(BizplanDto.ContractRequest::sno).toList());
        Map<Integer, Bbizcm> bySno = bbizcmRepository.findByAbusMngNoOrderBySnoAsc(abusMngNo).stream()
                .collect(Collectors.toMap(Bbizcm::getSno, Function.identity()));
        Set<Integer> incoming = new HashSet<>();
        for (BizplanDto.ContractRequest row : rows) {
            incoming.add(row.sno());
            Bbizcm existing = bySno.get(row.sno());
            if (existing != null) {
                if ("Y".equals(existing.getDelYn())) {
                    existing.restore();
                }
                existing.updateContract(row.cttNm(), row.nowCttManrC(), row.cttTrmMmNbr());
            } else {
                bbizcmRepository.save(Bbizcm.builder()
                        .abusMngNo(abusMngNo).sno(row.sno())
                        .cttNm(row.cttNm()).nowCttManrC(row.nowCttManrC())
                        .cttTrmMmNbr(row.cttTrmMmNbr())
                        .build());
            }
        }
        softDeleteMissing(bySno.values(), Bbizcm::getSno, incoming, Bbizcm::delete);
        return incoming;
    }

    private static <T extends com.kdb.it.domain.entity.BaseEntity> void softDeleteMissing(
            java.util.Collection<T> existing, Function<T, Integer> snoOf,
            Set<Integer> incoming, java.util.function.Consumer<T> deleter) {
        for (T row : existing) {
            boolean active = !"Y".equals(row.getDelYn());
            if (active && !incoming.contains(snoOf.apply(row))) {
                deleter.accept(row);
            }
        }
    }

    private BizplanDto.Detail toDetail(Bbizpm plan) {
        String abusMngNo = plan.getAbusMngNo();
        List<BizplanDto.Schedule> schedules = bbizsmRepository.findByAbusMngNoOrderBySnoAsc(abusMngNo).stream()
                .filter(r -> !"Y".equals(r.getDelYn()))
                .map(r -> new BizplanDto.Schedule(r.getSno(), r.getDsdCone(), r.getSttDt(), r.getEndDt()))
                .toList();
        List<BizplanDto.Item> items = bbizgmRepository.findByAbusMngNoOrderBySnoAsc(abusMngNo).stream()
                .filter(r -> !"Y".equals(r.getDelYn()))
                .map(r -> new BizplanDto.Item(r.getSno(), r.getGclNm(), r.getIoeC(), r.getQty(),
                        r.getAmt(), r.getFcAmt(), r.getCurC(), r.getXcr(), r.getXcrBseDt(), r.getCttSno()))
                .toList();
        List<BizplanDto.Contract> contracts = bbizcmRepository.findByAbusMngNoOrderBySnoAsc(abusMngNo).stream()
                .filter(r -> !"Y".equals(r.getDelYn()))
                .map(r -> new BizplanDto.Contract(r.getSno(), r.getCttNm(), r.getNowCttManrC(), r.getCttTrmMmNbr()))
                .toList();
        return new BizplanDto.Detail(abusMngNo, plan.getAbusNm(), plan.getBgNo(), plan.getTotRqmAmt(),
                plan.getItPtlEdrtTc(), plan.getRedtConeInf(), currentStatus(abusMngNo),
                schedules, items, contracts);
    }
}
