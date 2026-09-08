package com.kdb.it.domain.budget.cost.service;

import static com.kdb.it.domain.budget.cost.service.CostNameSnapshotResolver.snapshotOrResolved;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.util.BudgetAmountCalculator;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 전산업무비에 연결된 금융정보단말기(BTERMM) 행의 생성·동기화를 담당합니다.
 *
 * <p>{@link CostService}의 등록·수정 경로가 공유하던 단말 CUD 블록을 모읍니다. 채번·환율 재계산·담당자 조직코드 보정·엔티티 조립이 두 경로에 나뉘어
 * 있으면 한쪽에서만 정규화가 빠지는 식으로 조용히 갈라지므로, 단말을 만드는 경로를 이 컴포넌트로 좁힙니다. 트랜잭션 경계는 호출자인 서비스가 소유합니다.
 */
@Component
@RequiredArgsConstructor
public class CostTerminalSynchronizer {

    private final BtermmRepository btermmRepository;
    private final UserRepository cuserIRepository;
    private final OrgNameResolver orgNameResolver;
    private final XcrLookupService xcrLookupService;
    private final CostNameSnapshotResolver nameResolver;

    /**
     * 신규 전산업무비에 요청 단말기를 모두 저장합니다.
     *
     * @param cost 저장이 끝난 부모 원장
     * @param terminals 요청 단말 목록. null이면 아무것도 하지 않습니다
     * @param preserveSubmittedAmounts true면 이관 경로로 보고 환율 재계산과 담당자 사번 저장을 생략합니다
     * @param idYear 단말 관리번호 채번에 쓸 연도
     */
    public void createAll(
            Bcostm cost,
            List<CostDto.TerminalDto> terminals,
            boolean preserveSubmittedAmounts,
            int idYear) {
        applyTerminalOrgCodes(terminals);
        if (terminals == null) {
            return;
        }
        for (CostDto.TerminalDto terminal : terminals) {
            if (preserveSubmittedAmounts) {
                /* 금융정보단말기 신규 생성은 업로드 담당자 행번을 담당자ID로 저장하지 않는다. */
                terminal.setCgprId(null);
            }
            if (terminal.getTmnMngNo() == null || terminal.getTmnMngNo().isEmpty()) {
                terminal.setTmnMngNo(generateTmnMngNo(idYear));
            }
            if (terminal.getSno() == null) {
                terminal.setSno(1);
            }
            if (!preserveSubmittedAmounts) {
                reconcileTerminalAmount(terminal);
            }
            saveNewTerminal(cost, terminal);
        }
    }

    /**
     * 수정 요청의 단말 목록과 기존 활성 단말을 병합합니다.
     *
     * <p>기본키가 일치하는 행은 제자리 수정, 기본키가 없는 행은 신규 저장, 요청에 없는 기존 행은 사용자 경로에서만 논리 삭제합니다. 이관 경로는 기본키 대신 업무
     * 필드로 기존 행을 찾고 요청에 없는 행을 지우지 않습니다.
     *
     * @param target 잠금이 걸린 부모 개정본
     * @param request 수정 요청. 단말 목록이 null이면 빈 목록으로 봅니다
     * @param preserveSubmittedAmounts true면 이관 경로로 보고 환율 재계산·논리 삭제를 생략합니다
     * @throws NumberFormatException 이관 경로에서 새 단말을 채번해야 하는데 요청의 예산연도가 숫자가 아닌 경우
     */
    public void sync(
            Bcostm target, CostDto.UpdateRequest request, boolean preserveSubmittedAmounts) {
        List<Btermm> existingTerminals =
                btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(
                        target.getCostBgNo(), target.getBgSno(), "N");
        Map<String, Btermm> existingByPk =
                existingTerminals.stream()
                        .collect(
                                Collectors.toMap(
                                        terminal ->
                                                terminalPk(
                                                        terminal.getTmnMngNo(), terminal.getSno()),
                                        terminal -> terminal,
                                        (first, second) -> first));
        List<CostDto.TerminalDto> requestedTerminals =
                request.getTerminals() != null ? request.getTerminals() : List.of();
        applyTerminalOrgCodes(requestedTerminals);
        Set<String> keptPks = new HashSet<>();
        for (CostDto.TerminalDto terminal : requestedTerminals) {
            if (!preserveSubmittedAmounts) {
                reconcileTerminalAmount(terminal);
            }
            Btermm existing =
                    terminal.getTmnMngNo() != null && terminal.getSno() != null
                            ? existingByPk.get(
                                    terminalPk(terminal.getTmnMngNo(), terminal.getSno()))
                            : null;
            if (existing == null && preserveSubmittedAmounts) {
                existing =
                        existingTerminals.stream()
                                .filter(
                                        candidate ->
                                                !keptPks.contains(
                                                        terminalPk(
                                                                candidate.getTmnMngNo(),
                                                                candidate.getSno())))
                                .filter(candidate -> matchesMigrationTerminal(candidate, terminal))
                                .findFirst()
                                .orElse(null);
            }
            if (existing != null) {
                updateTerminal(existing, terminal, preserveSubmittedAmounts);
                existing.assignCgprName(nameResolver.resolveCgprName(existing.getCgprId()));
                existing.assignCgprName(terminal.getCgprNm());
                keptPks.add(terminalPk(existing.getTmnMngNo(), existing.getSno()));
            } else {
                if (terminal.getTmnMngNo() == null || terminal.getTmnMngNo().isEmpty()) {
                    terminal.setTmnMngNo(
                            generateTmnMngNo(
                                    preserveSubmittedAmounts
                                            ? Integer.parseInt(request.getBseYy())
                                            : LocalDate.now().getYear()));
                }
                if (terminal.getSno() == null) {
                    terminal.setSno(1);
                }
                saveNewTerminal(target, terminal);
                keptPks.add(terminalPk(terminal.getTmnMngNo(), terminal.getSno()));
            }
        }
        if (!preserveSubmittedAmounts) {
            existingTerminals.stream()
                    .filter(
                            terminal ->
                                    !keptPks.contains(
                                            terminalPk(terminal.getTmnMngNo(), terminal.getSno())))
                    .forEach(Btermm::delete);
        }
    }

    private void saveNewTerminal(Bcostm cost, CostDto.TerminalDto terminal) {
        Btermm entity = terminal.toEntity();
        entity.setBcostmInfo(cost.getCostBgNo(), cost.getBgSno());
        entity.assignCgprName(nameResolver.resolveCgprName(entity.getCgprId()));
        entity.assignCgprName(terminal.getCgprNm());
        assignTerminalOrgNames(entity, terminal);
        btermmRepository.save(entity);
    }

    private static boolean matchesMigrationTerminal(
            Btermm existing, CostDto.TerminalDto requested) {
        return Objects.equals(existing.getSpfTmnNm(), requested.getSpfTmnNm())
                && Objects.equals(existing.getTmnKdTc(), requested.getTmnKdTc())
                && Objects.equals(existing.getTmnClsfC(), requested.getTmnClsfC())
                && matchesNullableIdentity(
                        requested.getCgprId(),
                        existing.getCgprId(),
                        requested.getCgprNm(),
                        existing.getCgprNm())
                && matchesNullableIdentity(
                        requested.getTermSvnDpmC(),
                        existing.getTermSvnDpmC(),
                        requested.getTermSvnDpmNm(),
                        existing.getSvnDpmNm())
                && matchesNullableIdentity(
                        requested.getTermSvnTemC(),
                        existing.getTermSvnTemC(),
                        requested.getTermSvnTemNm(),
                        existing.getSvnTemNm());
    }

    private static boolean matchesNullableIdentity(
            String requestedCode, String existingCode, String requestedName, String existingName) {
        if (StringUtils.hasText(requestedCode)) {
            return Objects.equals(requestedCode, existingCode);
        }
        if (StringUtils.hasText(requestedName) && StringUtils.hasText(existingName)) {
            return requestedName.trim().equals(existingName.trim());
        }
        return true;
    }

    private void reconcileTerminalAmount(CostDto.TerminalDto terminal) {
        terminal.setXcr(xcrLookupService.resolveXcr(terminal.getCurC(), LocalDate.now()));
        BigDecimal[] reconciled =
                BudgetAmountCalculator.reconcileAmount(
                        terminal.getFcAmt(),
                        terminal.getTermRqmBgAmt(),
                        terminal.getCurC(),
                        terminal.getXcr());
        terminal.setTermRqmBgAmt(reconciled[0]);
        terminal.setFcAmt(reconciled[1]);
    }

    private void updateTerminal(
            Btermm existing, CostDto.TerminalDto terminal, boolean preserveSubmittedAmounts) {
        if (preserveSubmittedAmounts) {
            if (!StringUtils.hasText(terminal.getCgprId())) {
                terminal.setCgprId(existing.getCgprId());
            }
            if (!StringUtils.hasText(terminal.getTermSvnDpmC())) {
                terminal.setTermSvnDpmC(existing.getTermSvnDpmC());
            }
            if (!StringUtils.hasText(terminal.getTermSvnTemC())) {
                terminal.setTermSvnTemC(existing.getTermSvnTemC());
            }
        }
        existing.update(toTerminalUpdateCommand(terminal));
        assignTerminalOrgNames(existing, terminal);
    }

    private void assignTerminalOrgNames(Btermm entity, CostDto.TerminalDto terminal) {
        entity.assignSvnOrgNames(
                snapshotOrResolved(
                        terminal.getTermSvnDpmNm(),
                        orgNameResolver.resolveName(entity.getTermSvnDpmC())),
                snapshotOrResolved(
                        terminal.getTermSvnTemNm(),
                        orgNameResolver.resolveName(entity.getTermSvnTemC())));
    }

    private static Btermm.UpdateCommand toTerminalUpdateCommand(CostDto.TerminalDto terminal) {
        return Btermm.UpdateCommand.builder()
                .spfTmnNm(terminal.getSpfTmnNm())
                .tmnKdTc(terminal.getTmnKdTc())
                .nsfUsgCone(terminal.getNsfUsgCone())
                .tmnClsfC(terminal.getTmnClsfC())
                .termRqmBgAmt(terminal.getTermRqmBgAmt())
                .curC(terminal.getCurC())
                .xcr(terminal.getXcr())
                .xcrBseDt(DateFormatUtil.toYmd8(terminal.getXcrBseDt()))
                .dfrCleC(terminal.getDfrCleC())
                .indRsn(terminal.getIndRsn())
                .cgprId(terminal.getCgprId())
                .termSvnTemC(terminal.getTermSvnTemC())
                .svnTemNm(terminal.getTermSvnTemNm())
                .termSvnDpmC(terminal.getTermSvnDpmC())
                .svnDpmNm(terminal.getTermSvnDpmNm())
                .rmk(terminal.getRmk())
                .fcAmt(terminal.getFcAmt())
                .build();
    }

    /** 담당자 사번이 있는 단말은 담당자의 소속 팀·부점 코드로 조직 코드를 덮어씁니다. */
    private void applyTerminalOrgCodes(List<CostDto.TerminalDto> terminals) {
        if (terminals == null || terminals.isEmpty()) {
            return;
        }
        Set<String> userIds =
                terminals.stream()
                        .map(CostDto.TerminalDto::getCgprId)
                        .filter(userId -> userId != null && !userId.isBlank())
                        .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, CuserI> usersById =
                cuserIRepository.findByEnoIn(userIds).stream()
                        .collect(
                                Collectors.toMap(
                                        CuserI::getEno, user -> user, (first, second) -> first));
        for (CostDto.TerminalDto terminal : terminals) {
            CuserI user = usersById.get(terminal.getCgprId());
            if (user != null) {
                terminal.setTermSvnTemC(user.getTemC());
                terminal.setTermSvnDpmC(user.getBbrC());
            }
        }
    }

    private String generateTmnMngNo(int idYear) {
        Long sequence = btermmRepository.getNextSequenceValue();
        return String.format("TER-%s-%04d", idYear, sequence);
    }

    private static String terminalPk(String terminalNo, Integer sno) {
        return terminalNo + "_" + sno;
    }
}
