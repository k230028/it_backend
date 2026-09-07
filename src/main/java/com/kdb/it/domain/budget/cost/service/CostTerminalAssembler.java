package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 전산업무비 응답에 연결 단말기와 담당자·코드명을 조립합니다. */
@Component
@RequiredArgsConstructor
public class CostTerminalAssembler {

    private final BtermmRepository terminalRepository;
    private final UserRepository userRepository;
    private final CodeNameMapBuilder codeNameMapBuilder;

    /**
     * 단건 개정본의 활성 단말기를 조회합니다.
     *
     * <p>동시성 스탬프 계산과 응답 조립이 같은 집합({@code DEL_YN='N'})을 쓰도록 조회 지점을 이 메서드 하나로 모읍니다.
     *
     * @param costBgNo 전산업무비 관리번호
     * @param bgSno 개정본 순번
     * @return 활성 단말기 엔티티 목록. 없으면 빈 목록입니다.
     */
    public List<Btermm> loadActiveTerminals(String costBgNo, Integer bgSno) {
        return terminalRepository.findByTermBgNoAndTermBgSnoAndDelYn(costBgNo, bgSno, "N");
    }

    /**
     * 여러 개정본의 활성 단말기를 IN 조회 한 번으로 읽어 개정본별로 묶습니다.
     *
     * <p>행마다 조회하지 않습니다. 목록 페이지 크기만큼 N+1이 생기는 것을 막기 위한 계약입니다. 단말기 보유 여부({@code TMN_YN})로 대상을 좁히지
     * 않습니다. 스탬프는 실제 활성 단말 집합으로 계산해야 하고, {@code TMN_YN='N'}인데 활성 단말이 남아 있는 행을 빼면 상세 조회 스탬프와 값이 갈려
     * 저장이 무조건 409가 됩니다.
     *
     * @param costs 조회 대상 비용 행
     * @return {@link #revisionKey(String, Integer)} 키의 활성 단말기 목록 맵. 입력이 비면 빈 맵입니다.
     */
    public Map<String, List<Btermm>> loadActiveTerminals(List<Bcostm> costs) {
        List<String> costBgNos = costs.stream().map(Bcostm::getCostBgNo).distinct().toList();
        if (costBgNos.isEmpty()) {
            return Map.of();
        }
        return terminalRepository.findByTermBgNoInAndDelYn(costBgNos, "N").stream()
                .collect(
                        Collectors.groupingBy(
                                terminal ->
                                        revisionKey(
                                                terminal.getTermBgNo(), terminal.getTermBgSno())));
    }

    /**
     * 개정본을 식별하는 단말기 그룹 키를 만듭니다.
     *
     * @param costBgNo 전산업무비 관리번호
     * @param bgSno 개정본 순번
     * @return 관리번호와 순번을 결합한 키
     */
    public static String revisionKey(String costBgNo, Integer bgSno) {
        return costBgNo + "_" + bgSno;
    }

    /**
     * 단건 응답에 이미 조회한 활성 단말기와 표시명을 조립합니다.
     *
     * @param response 단말기를 연결할 전산업무비 응답
     * @param terminals {@link #loadActiveTerminals(String, Integer)}로 읽은 활성 단말기
     */
    public void attach(CostDto.Response response, List<Btermm> terminals) {
        List<CostDto.TerminalDto> dtos =
                terminals.stream().map(CostDto.TerminalDto::fromEntity).toList();
        enrichNames(dtos);
        response.setTerminals(dtos);
    }

    /**
     * 목록 응답에 단말기 보유 행만 골라 조립합니다.
     *
     * @param costs 응답의 원본 비용 행
     * @param responses 원본과 같은 순서의 응답
     * @param terminalsByRevision {@link #loadActiveTerminals(List)} 결과
     */
    public void attachList(
            List<Bcostm> costs,
            List<CostDto.Response> responses,
            Map<String, List<Btermm>> terminalsByRevision) {
        attachBatch(costs, responses, terminalsByRevision, cost -> "Y".equals(cost.getTmnYn()));
    }

    /**
     * 일괄 조회 응답에 관리번호별 활성 단말기를 조립합니다.
     *
     * @param costs 일괄 조회에서 선택된 대표 비용 행
     * @param responses 원본과 같은 순서의 응답
     * @param terminalsByRevision {@link #loadActiveTerminals(List)} 결과
     */
    public void attachBulk(
            List<Bcostm> costs,
            List<CostDto.Response> responses,
            Map<String, List<Btermm>> terminalsByRevision) {
        attachBatch(costs, responses, terminalsByRevision, cost -> true);
    }

    private void attachBatch(
            List<Bcostm> costs,
            List<CostDto.Response> responses,
            Map<String, List<Btermm>> terminalsByRevision,
            java.util.function.Predicate<Bcostm> terminalTarget) {
        if (terminalsByRevision.isEmpty()) {
            return;
        }
        Map<String, List<CostDto.TerminalDto>> dtosByKey = new LinkedHashMap<>();
        terminalsByRevision.forEach(
                (key, terminals) ->
                        dtosByKey.put(
                                key,
                                terminals.stream().map(CostDto.TerminalDto::fromEntity).toList()));
        enrichNames(dtosByKey.values().stream().flatMap(List::stream).toList());
        for (int index = 0; index < costs.size(); index++) {
            Bcostm cost = costs.get(index);
            if (!terminalTarget.test(cost)) {
                continue;
            }
            responses
                    .get(index)
                    .setTerminals(
                            dtosByKey.getOrDefault(
                                    revisionKey(cost.getCostBgNo(), cost.getBgSno()), List.of()));
        }
    }

    private void enrichNames(List<CostDto.TerminalDto> terminals) {
        if (terminals.isEmpty()) {
            return;
        }
        Set<String> userIds = collect(terminals, CostDto.TerminalDto::getCgprId);
        if (!userIds.isEmpty()) {
            Map<String, String> userNames =
                    userRepository.findNameViewsByEnoIn(userIds).stream()
                            .collect(
                                    Collectors.toMap(
                                            UserRepository.UserNameView::getEno,
                                            UserRepository.UserNameView::getUsrNm));
            /* 조회 실패(빈 행번·미등록 행번)는 null로 덮지 않고 저장 스냅샷(cgprNm 초기값)을 유지한다 */
            terminals.forEach(
                    terminal -> {
                        String userName = userNames.get(terminal.getCgprId());
                        if (userName != null && !userName.isBlank()) {
                            terminal.setCgprNm(userName);
                        }
                    });
        }
        Map<String, String> serviceNames =
                codeNameMapBuilder.build(
                        CommonCodeGroups.TERM_SERVICE,
                        collect(terminals, CostDto.TerminalDto::getTmnClsfC));
        Map<String, String> kindNames =
                codeNameMapBuilder.build(
                        CommonCodeGroups.TERM_KIND,
                        collect(terminals, CostDto.TerminalDto::getTmnKdTc));
        Map<String, String> paymentNames =
                codeNameMapBuilder.build(
                        CommonCodeGroups.DFR_CLE,
                        collect(terminals, CostDto.TerminalDto::getDfrCleC));
        terminals.forEach(
                terminal -> {
                    if (terminal.getTmnClsfC() != null) {
                        terminal.setTmnClsfCNm(serviceNames.get(terminal.getTmnClsfC()));
                    }
                    if (terminal.getTmnKdTc() != null) {
                        terminal.setTmnKdTcNm(kindNames.get(terminal.getTmnKdTc()));
                    }
                    if (terminal.getDfrCleC() != null) {
                        terminal.setDfrCleCNm(paymentNames.get(terminal.getDfrCleC()));
                    }
                });
    }

    private static Set<String> collect(
            List<CostDto.TerminalDto> terminals, Function<CostDto.TerminalDto, String> extractor) {
        return terminals.stream()
                .map(extractor)
                .filter(value -> value != null && !value.isEmpty())
                .collect(Collectors.toSet());
    }
}
