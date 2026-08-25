package com.kdb.it.domain.budget.document.formguide;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 사업 입력 폼에서 안내 본문을 등록할 수 있는 고정 대상 카탈로그입니다. */
public final class FormGuideCatalog {

    private static final List<Entry> ENTRIES =
            Stream.concat(
                            Stream.concat(
                                    List.of(
                                            entry(
                                                    "info.basic.bgYy",
                                                    FormGuideScope.INFO,
                                                    "기본 정보",
                                                    "사업연도",
                                                    "Select"),
                                            entry(
                                                    "info.basic.pulDtt",
                                                    FormGuideScope.INFO,
                                                    "기본 정보",
                                                    "사업구분",
                                                    "Select"),
                                            entry(
                                                    "info.basic.abusNm",
                                                    FormGuideScope.INFO,
                                                    "기본 정보",
                                                    "사업명",
                                                    "AutoComplete"),
                                            entry(
                                                    "info.overview.prjDes",
                                                    FormGuideScope.INFO,
                                                    "사업 개요",
                                                    "사업 상세 설명",
                                                    "TiptapEditor"),
                                            entry(
                                                    "info.overview.saf",
                                                    FormGuideScope.INFO,
                                                    "사업 개요",
                                                    "현황",
                                                    "Textarea"),
                                            entry(
                                                    "info.overview.ncs",
                                                    FormGuideScope.INFO,
                                                    "사업 개요",
                                                    "필요성",
                                                    "Textarea"),
                                            entry(
                                                    "info.overview.xptEff",
                                                    FormGuideScope.INFO,
                                                    "사업 개요",
                                                    "기대효과",
                                                    "Textarea"),
                                            entry(
                                                    "info.overview.plm",
                                                    FormGuideScope.INFO,
                                                    "사업 개요",
                                                    "미추진 시 문제점",
                                                    "Textarea"),
                                            entry(
                                                    "info.scope.prjRng",
                                                    FormGuideScope.INFO,
                                                    "사업 범위",
                                                    "사업범위",
                                                    "TiptapEditor"),
                                            entry(
                                                    "info.progress.pulPsg",
                                                    FormGuideScope.INFO,
                                                    "진행 상황",
                                                    "추진 경과",
                                                    "Textarea"),
                                            entry(
                                                    "info.progress.hrfPln",
                                                    FormGuideScope.INFO,
                                                    "진행 상황",
                                                    "향후 계획",
                                                    "Textarea"),
                                            entry(
                                                    "info.classification.bzDtt",
                                                    FormGuideScope.INFO,
                                                    "사업 구분",
                                                    "업무구분",
                                                    "MultiSelect"),
                                            entry(
                                                    "info.classification.prjTp",
                                                    FormGuideScope.INFO,
                                                    "사업 구분",
                                                    "사업유형",
                                                    "MultiSelect"),
                                            entry(
                                                    "info.classification.tchnTp",
                                                    FormGuideScope.INFO,
                                                    "사업 구분",
                                                    "기술유형",
                                                    "MultiSelect"),
                                            entry(
                                                    "info.classification.mnUsr",
                                                    FormGuideScope.INFO,
                                                    "사업 구분",
                                                    "주요사용자",
                                                    "MultiSelect"),
                                            entry(
                                                    "info.criteria.dplYn",
                                                    FormGuideScope.INFO,
                                                    "편성 기준",
                                                    "중복여부",
                                                    "Select"),
                                            entry(
                                                    "info.criteria.lblFsgTlm",
                                                    FormGuideScope.INFO,
                                                    "편성 기준",
                                                    "법규상 완료시기",
                                                    "DatePicker"),
                                            entry(
                                                    "info.department.svnHdq",
                                                    FormGuideScope.INFO,
                                                    "담당부서",
                                                    "주관부문",
                                                    "InputText"),
                                            entry(
                                                    "info.department.svnDpm",
                                                    FormGuideScope.INFO,
                                                    "담당부서",
                                                    "주관부서",
                                                    "InputText"),
                                            entry(
                                                    "info.department.svnDpmTlr",
                                                    FormGuideScope.INFO,
                                                    "담당부서",
                                                    "주관부서 팀장",
                                                    "InputText"),
                                            entry(
                                                    "info.department.svnDpmCgpr",
                                                    FormGuideScope.INFO,
                                                    "담당부서",
                                                    "주관부서 담당자",
                                                    "InputText"),
                                            entry(
                                                    "info.department.itDpm",
                                                    FormGuideScope.INFO,
                                                    "담당부서",
                                                    "IT부서",
                                                    "InputText"),
                                            entry(
                                                    "info.department.itDpmTlr",
                                                    FormGuideScope.INFO,
                                                    "담당부서",
                                                    "IT부서 팀장",
                                                    "InputText"),
                                            entry(
                                                    "info.department.itDpmCgpr",
                                                    FormGuideScope.INFO,
                                                    "담당부서",
                                                    "IT부서 담당자",
                                                    "InputText"),
                                            entry(
                                                    "info.schedule.dfrAmt",
                                                    FormGuideScope.INFO,
                                                    "추진시기 및 소요예산",
                                                    "기 지급금액",
                                                    "InputNumber"),
                                            entry(
                                                    "info.schedule.rprSts",
                                                    FormGuideScope.INFO,
                                                    "추진시기 및 소요예산",
                                                    "보고상태",
                                                    "Select"),
                                            entry(
                                                    "info.schedule.sttDt",
                                                    FormGuideScope.INFO,
                                                    "추진시기 및 소요예산",
                                                    "시작일",
                                                    "DatePicker"),
                                            entry(
                                                    "info.schedule.endDt",
                                                    FormGuideScope.INFO,
                                                    "추진시기 및 소요예산",
                                                    "종료일",
                                                    "DatePicker"),
                                            entry(
                                                    "info.schedule.prjPulPtt",
                                                    FormGuideScope.INFO,
                                                    "추진시기 및 소요예산",
                                                    "사업추진 가능성",
                                                    "Select"),
                                            entry(
                                                    "cost.basic.bgYy",
                                                    FormGuideScope.COST,
                                                    "기본 정보",
                                                    "사업연도",
                                                    "Select"),
                                            entry(
                                                    "cost.basic.pulDtt",
                                                    FormGuideScope.COST,
                                                    "기본 정보",
                                                    "사업구분",
                                                    "Select"),
                                            entry(
                                                    "cost.basic.abusNm",
                                                    FormGuideScope.COST,
                                                    "기본 정보",
                                                    "사업명",
                                                    "AutoComplete"),
                                            entry(
                                                    "cost.overview.prjDes",
                                                    FormGuideScope.COST,
                                                    "사업 개요",
                                                    "사업 상세 설명",
                                                    "TiptapEditor"),
                                            entry(
                                                    "cost.overview.saf",
                                                    FormGuideScope.COST,
                                                    "사업 개요",
                                                    "현황",
                                                    "Textarea"),
                                            entry(
                                                    "cost.overview.plm",
                                                    FormGuideScope.COST,
                                                    "사업 개요",
                                                    "미추진 시 문제점",
                                                    "Textarea"),
                                            entry(
                                                    "cost.scope.prjRng",
                                                    FormGuideScope.COST,
                                                    "사업 범위",
                                                    "사업범위",
                                                    "TiptapEditor"),
                                            entry(
                                                    "cost.department.svnHdq",
                                                    FormGuideScope.COST,
                                                    "담당부서",
                                                    "주관부문",
                                                    "InputText"),
                                            entry(
                                                    "cost.department.svnDpm",
                                                    FormGuideScope.COST,
                                                    "담당부서",
                                                    "주관부서",
                                                    "InputText"),
                                            entry(
                                                    "cost.department.svnDpmTlr",
                                                    FormGuideScope.COST,
                                                    "담당부서",
                                                    "주관부서 팀장",
                                                    "InputText"),
                                            entry(
                                                    "cost.department.svnDpmCgpr",
                                                    FormGuideScope.COST,
                                                    "담당부서",
                                                    "주관부서 담당자",
                                                    "InputText"))
                                            .stream(),
                                    resourceEntries(FormGuideScope.INFO).stream()),
                            resourceEntries(FormGuideScope.COST).stream())
                    .toList();

    private static final Map<String, Entry> BY_ID =
            ENTRIES.stream().collect(Collectors.toUnmodifiableMap(Entry::guideId, entry -> entry));

    private FormGuideCatalog() {}

    /**
     * 고정 카탈로그에서 ID에 대응하는 대상 필드를 찾습니다.
     *
     * @param guideId 조회할 안정된 길라잡이 ID
     * @return 대상 필드 메타데이터
     * @throws IllegalArgumentException 지원하지 않는 ID일 때
     */
    public static Entry require(String guideId) {
        Entry entry = BY_ID.get(guideId);
        if (entry == null) {
            throw new IllegalArgumentException("지원하지 않는 길라잡이 ID입니다");
        }
        return entry;
    }

    /**
     * 지정한 사업 유형에서 관리 가능한 필드 목록을 반환합니다.
     *
     * @param scope 정보화사업 또는 경상사업 범위
     * @return 해당 범위의 고정 카탈로그 항목
     */
    public static List<Entry> entries(FormGuideScope scope) {
        return ENTRIES.stream().filter(entry -> entry.scope() == scope).toList();
    }

    private static Entry entry(
            String guideId,
            FormGuideScope scope,
            String section,
            String fieldLabel,
            String controlType) {
        return new Entry(guideId, scope, section, fieldLabel, controlType);
    }

    private static List<Entry> resourceEntries(FormGuideScope scope) {
        String prefix = scope.guideIdPrefix() + "resource.";
        return List.of(
                entry(prefix + "ioe", scope, "소요자원 상세내용", "구분", "IoeCategorySelect"),
                entry(prefix + "item", scope, "소요자원 상세내용", "항목", "Textarea"),
                entry(prefix + "quantity", scope, "소요자원 상세내용", "수량", "InputNumber"),
                entry(prefix + "currency", scope, "소요자원 상세내용", "통화", "Select"),
                entry(prefix + "gclAmt", scope, "소요자원 상세내용", "당해 요청금액", "InputNumber"),
                entry(prefix + "laterAmt", scope, "소요자원 상세내용", "내년 이후 요청금액", "InputNumber"),
                entry(prefix + "basis", scope, "소요자원 상세내용", "산정근거", "Textarea"),
                entry(prefix + "introDate", scope, "소요자원 상세내용", "도입시기", "DatePicker"),
                entry(prefix + "paymentCycle", scope, "소요자원 상세내용", "지급주기", "Select"),
                entry(prefix + "infoProtection", scope, "소요자원 상세내용", "정보보호 여부", "Select"),
                entry(prefix + "integratedInfra", scope, "소요자원 상세내용", "통합인프라 여부", "Select"));
    }

    /** 사업 입력 길라잡이 대상 필드의 고정 메타데이터입니다. */
    public record Entry(
            String guideId,
            FormGuideScope scope,
            String section,
            String fieldLabel,
            String controlType) {}
}
