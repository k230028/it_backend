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
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.BprojaId;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import java.math.BigDecimal;
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
    /** BPROJA 사업계획 단계 키 접두사 — 협의회가 원본 ABUS_MNG_NO를 키로 쓰므로 충돌 회피 */
    static final String BPROJA_KEY_PREFIX = "BIZ-";
    /** BPROJA 예산편성 단계 키 접두사 (BG_NO 자동 연계용) */
    private static final String BG_KEY_PREFIX = "BG-";

    private final BizplanRepository bizplanRepository;
    private final BbizsmRepository bbizsmRepository;
    private final BbizgmRepository bbizgmRepository;
    private final BbizcmRepository bbizcmRepository;
    private final ProjectRepository projectRepository;
    private final BplanaRepository bplanaRepository;
    private final BprojaRepository bprojaRepository;
    private final BprojaSyncService bprojaSyncService;

    public List<BizplanDto.ListItem> list(CustomUserDetails user) {
        String bbrC = user.isAdmin() ? null : user.getBbrC();
        return bizplanRepository.search(bbrC);
    }

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
        return toDetail(plan);
    }

    public BizplanDto.Detail get(String abusMngNo, CustomUserDetails user) {
        Bprojm project = loadEligibleProject(abusMngNo);
        verifyDeptOrAdmin(project.getSvnDpmC(), user);
        return toDetail(loadPlan(abusMngNo));
    }

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
