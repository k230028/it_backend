package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
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
     * 단건 응답에 활성 단말기와 표시명을 조립합니다.
     *
     * @param response 단말기를 연결할 전산업무비 응답
     */
    public void attach(CostDto.Response response) {
        List<CostDto.TerminalDto> terminals =
                terminalRepository
                        .findByTermBgNoAndTermBgSnoAndDelYn(
                                response.getCostBgNo(), response.getBgSno(), "N")
                        .stream()
                        .map(CostDto.TerminalDto::fromEntity)
                        .toList();
        enrichNames(terminals);
        response.setTerminals(terminals);
    }

    /**
     * 목록 응답에 활성 단말기를 한 번에 조회해 조립합니다.
     *
     * @param costs 응답의 원본 비용 행
     * @param responses 원본과 같은 순서의 응답
     */
    public void attachList(List<Bcostm> costs, List<CostDto.Response> responses) {
        attachBatch(costs, responses, cost -> "Y".equals(cost.getTmnYn()));
    }

    /**
     * 일괄 조회 응답에 관리번호별 활성 단말기를 한 번에 조회해 조립합니다.
     *
     * @param costs 일괄 조회에서 선택된 대표 비용 행
     * @param responses 원본과 같은 순서의 응답
     */
    public void attachBulk(List<Bcostm> costs, List<CostDto.Response> responses) {
        attachBatch(costs, responses, cost -> true);
    }

    private void attachBatch(
            List<Bcostm> costs,
            List<CostDto.Response> responses,
            java.util.function.Predicate<Bcostm> terminalTarget) {
        List<String> terminalCostNos =
                costs.stream().filter(terminalTarget).map(Bcostm::getCostBgNo).distinct().toList();
        if (terminalCostNos.isEmpty()) {
            return;
        }
        Map<String, List<Btermm>> terminalsByKey =
                terminalRepository.findByTermBgNoInAndDelYn(terminalCostNos, "N").stream()
                        .collect(
                                Collectors.groupingBy(
                                        terminal ->
                                                key(
                                                        terminal.getTermBgNo(),
                                                        terminal.getTermBgSno())));
        for (int index = 0; index < costs.size(); index++) {
            Bcostm cost = costs.get(index);
            if (!terminalTarget.test(cost)) {
                continue;
            }
            List<CostDto.TerminalDto> terminals =
                    terminalsByKey
                            .getOrDefault(key(cost.getCostBgNo(), cost.getBgSno()), List.of())
                            .stream()
                            .map(CostDto.TerminalDto::fromEntity)
                            .toList();
            enrichNames(terminals);
            responses.get(index).setTerminals(terminals);
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
            terminals.forEach(terminal -> terminal.setCgprNm(userNames.get(terminal.getCgprId())));
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

    private static String key(String costBgNo, Integer bgSno) {
        return costBgNo + "_" + bgSno;
    }
}
