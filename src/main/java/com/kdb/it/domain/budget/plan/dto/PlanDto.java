package com.kdb.it.domain.budget.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 정보기술부문 계획 관련 DTO 클래스 모음
 *
 * <p>
 * 정보기술부문계획(TPRMPP_BPLANM) 엔티티의 생성, 조회에 사용되는 DTO를
 * 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 *
 * <ul>
 * <li>{@link CreateRequest}: 계획 생성 요청 (대상년도, 계획구분, 프로젝트 목록)</li>
 * <li>{@link ListResponse}: 계획 목록 조회 응답 (요약 정보)</li>
 * <li>{@link DetailResponse}: 계획 상세 조회 응답 (JSON 스냅샷 포함)</li>
 * <li>{@link SnapshotDto}: PLN_DTL_CONE에 저장되는 JSON 스냅샷 구조</li>
 * </ul>
 */
public class PlanDto {

    /**
     * 계획 생성 요청 DTO
     *
     * <p>
     * 신규 계획 등록 시 대상년도, 계획구분, 대상 프로젝트 목록을 전달합니다.
     * 계획관리번호는 서비스에서 Oracle 시퀀스로 자동 채번됩니다.
     * (형식: {@code PLN-{bseYy}-{seq:04d}})
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "PlanCreateRequest")
    public static class CreateRequest {

        /** 대상년도 (형식: YYYY, 예: "2026") */
        @Schema(description = "대상년도 (YYYY)")
        private String bseYy;

        /** 계획구분 (신규, 조정) */
        @Schema(description = "계획구분 (신규/조정)")
        private String itPtlPlnTpC;

        /** 대상 프로젝트관리번호 목록 */
        @Schema(description = "대상 프로젝트관리번호 목록")
        private List<String> prjMngNos;

        /** 대상 전산업무비관리번호 목록 */
        @Schema(description = "대상 전산업무비관리번호 목록")
        private List<String> itMngcNos;

        /** IT프로젝트내용 */
        @Schema(description = "IT프로젝트내용")
        private String prjDvmCone;

        /** IT예산내용 */
        @Schema(description = "IT예산내용")
        private String itBgCone;

        /** IT예산비고 */
        @Schema(description = "IT예산비고")
        private String itPrjRmk;

        /** 자본예산비고 */
        @Schema(description = "자본예산비고")
        private String cpitBgRmk;

        /** 관리비예산비고 */
        @Schema(description = "관리비예산비고")
        private String mngcBgRmk;

        /** 예산배분 항목 목록 (수익/비용 구분별 배분 금액) */
        @Schema(description = "예산배분 항목 목록")
        private List<BudgetAllocationItem> budgetAllocation;

        /** 자본예산 항목 목록 (투자자산 구분별 예산) */
        @Schema(description = "자본예산 항목 목록")
        private List<CapitalBudgetItem> capitalBudget;

        /** 경비 항목 목록 (비용 구분별 경비) */
        @Schema(description = "경비 항목 목록")
        private List<ExpenseCostItem> expenseCost;

        // ── 카드 원천 데이터 (B안: 상세 화면 렌더링용 패스스루) ─────────────────
        /** 정보화사업 카드 원천 — PlanProjectItem[] (pulDtt 포함) */
        @Schema(description = "정보화사업 카드 원천 (PlanProjectItem)")
        private List<Map<String, Object>> prjSnapshots;

        /** 자본예산 카드 원천 — SummaryItem[] (capital=true) */
        @Schema(description = "자본예산 비목 요약")
        private List<Map<String, Object>> capitalSummaryItems;

        /** 일반관리비 카드 원천 — SummaryItem[] (capital=false) */
        @Schema(description = "일반관리비 비목 요약")
        private List<Map<String, Object>> expenseItems;

        /** 일반관리비 카드 주요내역용 — 전산업무비 상세 목록 */
        @Schema(description = "전산업무비 상세 (주요내역용)")
        private List<Map<String, Object>> costDetails;

        /** 일반관리비 카드 — itMngcNo → 부모 사업명 맵 */
        @Schema(description = "itMngcNo → 부모 사업명 맵")
        private Map<String, String> costPrjNm;
    }

    /**
     * 계획 텍스트 필드 수정 요청 DTO
     *
     * <p>
     * IT프로젝트내용, IT예산내용, IT예산비고, 자본예산비고, 관리비예산비고 5개 CLOB 필드를 갱신합니다.
     * null 값은 해당 필드를 null로 초기화합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "PlanUpdateRequest")
    public static class UpdateRequest {

        /** IT프로젝트내용 */
        @Schema(description = "IT프로젝트내용")
        private String prjDvmCone;

        /** IT예산내용 */
        @Schema(description = "IT예산내용")
        private String itBgCone;

        /** IT예산비고 */
        @Schema(description = "IT예산비고")
        private String itPrjRmk;

        /** 자본예산비고 */
        @Schema(description = "자본예산비고")
        private String cpitBgRmk;

        /** 관리비예산비고 */
        @Schema(description = "관리비예산비고")
        private String mngcBgRmk;
    }

    /**
     * 계획 목록 조회 응답 DTO
     *
     * <p>
     * 계획 조회 화면에서 데이터테이블 형태로 표시하는 요약 정보입니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "PlanListResponse")
    public static class ListResponse {

        /** 계획관리번호 (PK) */
        @Schema(description = "계획관리번호")
        private String reqDocNo;

        /** 계획구분 (신규, 조정) */
        @Schema(description = "계획구분")
        private String itPtlPlnTpC;

        /** 대상년도 (YYYY) */
        @Schema(description = "대상년도")
        private String bseYy;

        /** 총예산 */
        @Schema(description = "총예산")
        private BigDecimal aduTotAmt;

        /** 자본예산 */
        @Schema(description = "자본예산")
        private BigDecimal cpitBgApvAmt;

        /** 일반관리비 */
        @Schema(description = "일반관리비")
        private BigDecimal totXpAmt;

        /** 최초생성시간 */
        @Schema(description = "최초생성시간")
        private LocalDateTime fstEnrDtm;

        /** 최초생성자 사번 */
        @Schema(description = "최초생성자")
        private String fstEnrUsid;

        /** 최초생성자 이름 (CUSERI 조인 결과) */
        @Schema(description = "최초생성자 이름")
        private String fstEnrUsNm;

        /** 정보화사업 건수 (경상사업은 대표 신규 사업 1건으로 포함) */
        @Schema(description = "정보화사업 건수 (경상사업은 대표 신규 사업 1건으로 포함)")
        private Integer itPrjCnt;

        /** 신규 사업 건수 (정보화사업 중 PUL_DTT=신규) */
        @Schema(description = "신규 사업 건수 (정보화사업 중 PUL_DTT=신규)")
        private Integer newPrjCnt;

        /** 계속 사업 건수 (정보화사업 중 PUL_DTT=계속) */
        @Schema(description = "계속 사업 건수 (정보화사업 중 PUL_DTT=계속)")
        private Integer contPrjCnt;

        /**
         * {@link Bplanm} 엔티티에서 목록 응답 DTO로 변환합니다.
         *
         * @param plan 변환할 Bplanm 엔티티
         * @return 변환된 ListResponse DTO (카운트는 0으로 초기화)
         */
        public static ListResponse fromEntity(Bplanm plan) {
            return ListResponse.builder()
                    .reqDocNo(plan.getReqDocNo())
                    .itPtlPlnTpC(plan.getItPtlPlnTpC())
                    .bseYy(plan.getBseYy())
                    .aduTotAmt(plan.getAduTotAmt())
                    .cpitBgApvAmt(plan.getCpitBgApvAmt())
                    .totXpAmt(plan.getTotXpAmt())
                    .fstEnrDtm(plan.getFstEnrDtm())
                    .fstEnrUsid(plan.getFstEnrUsid())
                    .itPrjCnt(0)
                    .newPrjCnt(0)
                    .contPrjCnt(0)
                    .build();
        }

        /**
         * 목록 전용 프로젝션에서 응답 DTO를 생성합니다.
         *
         * @param plan 변환할 목록 프로젝션
         * @return 카운트가 0으로 초기화된 목록 응답
         */
        public static ListResponse fromView(BplanmRepository.PlanListView plan) {
            return ListResponse.builder()
                    .reqDocNo(plan.getReqDocNo())
                    .itPtlPlnTpC(plan.getItPtlPlnTpC())
                    .bseYy(plan.getBseYy())
                    .aduTotAmt(plan.getAduTotAmt())
                    .cpitBgApvAmt(plan.getCpitBgApvAmt())
                    .totXpAmt(plan.getTotXpAmt())
                    .fstEnrDtm(plan.getFstEnrDtm())
                    .fstEnrUsid(plan.getFstEnrUsid())
                    .itPrjCnt(0)
                    .newPrjCnt(0)
                    .contPrjCnt(0)
                    .build();
        }
    }

    /**
     * 계획 상세 조회 응답 DTO
     *
     * <p>
     * 계획 상세 화면에서 JSON 스냅샷과 연결된 프로젝트 목록을 포함합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "PlanDetailResponse")
    public static class DetailResponse {

        /** 계획관리번호 (PK) */
        @Schema(description = "계획관리번호")
        private String reqDocNo;

        /** 계획구분 (신규, 조정) */
        @Schema(description = "계획구분")
        private String itPtlPlnTpC;

        /** 대상년도 (YYYY) */
        @Schema(description = "대상년도")
        private String bseYy;

        /** 총예산 */
        @Schema(description = "총예산")
        private BigDecimal aduTotAmt;

        /** 자본예산 */
        @Schema(description = "자본예산")
        private BigDecimal cpitBgApvAmt;

        /** 일반관리비 */
        @Schema(description = "일반관리비")
        private BigDecimal totXpAmt;

        /**
         * 계획세부내용 JSON 스냅샷 문자열
         * <p>
         * 계획 저장 시점의 전체 프로젝트 데이터를 JSON으로 직렬화한 값입니다.
         * 프론트엔드에서 이 문자열을 파싱하여 예산 총계, 부문별/사업유형별 목록을 표시합니다.
         * </p>
         */
        @Schema(description = "계획세부내용 (JSON)")
        private String redtConeInf;

        /** IT프로젝트내용 */
        @Schema(description = "IT프로젝트내용")
        private String prjDvmCone;

        /** IT예산내용 */
        @Schema(description = "IT예산내용")
        private String itBgCone;

        /** IT예산비고 */
        @Schema(description = "IT예산비고")
        private String itPrjRmk;

        /** 자본예산비고 */
        @Schema(description = "자본예산비고")
        private String cpitBgRmk;

        /** 관리비예산비고 */
        @Schema(description = "관리비예산비고")
        private String mngcBgRmk;

        /** 연결된 프로젝트관리번호 목록 */
        @Schema(description = "연결된 프로젝트관리번호 목록")
        private List<String> prjMngNos;

        /** 최초생성시간 */
        @Schema(description = "최초생성시간")
        private LocalDateTime fstEnrDtm;

        /** 최초생성자 */
        @Schema(description = "최초생성자")
        private String fstEnrUsid;

        /**
         * {@link Bplanm} 엔티티에서 상세 응답 DTO로 변환합니다.
         *
         * @param plan      변환할 Bplanm 엔티티
         * @param prjMngNos 연결된 프로젝트관리번호 목록
         * @return 변환된 DetailResponse DTO
         */
        public static DetailResponse fromEntity(Bplanm plan, List<String> prjMngNos) {
            return DetailResponse.builder()
                    .reqDocNo(plan.getReqDocNo())
                    .itPtlPlnTpC(plan.getItPtlPlnTpC())
                    .bseYy(plan.getBseYy())
                    .aduTotAmt(plan.getAduTotAmt())
                    .cpitBgApvAmt(plan.getCpitBgApvAmt())
                    .totXpAmt(plan.getTotXpAmt())
                    .redtConeInf(plan.getRedtConeInf())
                    .prjDvmCone(plan.getPrjDvmCone())
                    .itBgCone(plan.getItBgCone())
                    .itPrjRmk(plan.getItPrjRmk())
                    .cpitBgRmk(plan.getCpitBgRmk())
                    .mngcBgRmk(plan.getMngcBgRmk())
                    .prjMngNos(prjMngNos)
                    .fstEnrDtm(plan.getFstEnrDtm())
                    .fstEnrUsid(plan.getFstEnrUsid())
                    .build();
        }
    }

    /**
     * PLN_DTL_INF 컬럼에 저장되는 JSON 스냅샷 구조
     *
     * <p>
     * 계획 저장 시점의 전체 프로젝트 데이터를 보관합니다.
     * ObjectMapper로 직렬화/역직렬화합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SnapshotDto {

        /** 대상년도 */
        private String bseYy;

        /** 계획구분 */
        private String itPtlPlnTpC;

        /** 총예산 합계 */
        private BigDecimal aduTotAmt;

        /** 자본예산 합계 */
        private BigDecimal cpitBgApvAmt;

        /** 일반관리비 합계 */
        private BigDecimal totXpAmt;

        /** 전체 대상 프로젝트 목록 */
        private List<ProjectSnapshot> projects;

        /** 부문(SVN_HDQ)별 프로젝트 목록 */
        private List<Map<String, Object>> byDepartment;

        /** 사업유형(PRJ_TP)별 프로젝트 목록 */
        private List<Map<String, Object>> byProjectType;

        /** 예산배분 항목 목록 */
        private List<BudgetAllocationItem> budgetAllocation;

        /** 자본예산 항목 목록 */
        private List<CapitalBudgetItem> capitalBudget;

        /** 경비 항목 목록 */
        private List<ExpenseCostItem> expenseCost;

        // ── 카드 원천 데이터 (B안: 상세 화면 렌더링용 패스스루) ─────────────────
        /** 정보화사업 카드 원천 — PlanProjectItem[] (pulDtt 포함) */
        private List<Map<String, Object>> prjSnapshots;

        /** 자본예산 카드 원천 — SummaryItem[] (capital=true) */
        private List<Map<String, Object>> capitalSummaryItems;

        /** 일반관리비 카드 원천 — SummaryItem[] (capital=false) */
        private List<Map<String, Object>> expenseItems;

        /** 일반관리비 카드 주요내역용 — 전산업무비 상세 목록 */
        private List<Map<String, Object>> costDetails;

        /** 일반관리비 카드 — itMngcNo → 부모 사업명 맵 */
        private Map<String, String> costPrjNm;
    }

    /**
     * 스냅샷 내 개별 프로젝트 정보
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProjectSnapshot {

        /** 프로젝트관리번호 */
        private String prjMngNo;

        /** 사업명 */
        private String abusNm;

        /** 사업유형 */
        private String prjTp;

        /** 사업구분 (신규/계속 코드) */
        private String pulDtt;

        /** 주관본부/부문 */
        private String svnHdq;

        /** 주관부서코드 */
        private String svnDpm;

        /** 주관부서명 */
        private String svnDpmNm;

        /** 프로젝트 예산 (총예산) */
        private BigDecimal prjBg;

        /** 자본예산 */
        private BigDecimal assetBg;

        /** 일반관리비 */
        private BigDecimal costBg;
    }

    /**
     * 예산배분 항목 — 수익/비용 구분별 배분 금액
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "BudgetAllocationItem")
    public static class BudgetAllocationItem {

        /** 수익/비용 구분 코드 */
        @Schema(description = "수익/비용 구분 코드")
        private String ioeC;

        /** 수익/비용 구분명 */
        @Schema(description = "수익/비용 구분명")
        private String ioeCNm;

        /** 배분 금액 */
        @Schema(description = "배분 금액")
        private BigDecimal allocBg;
    }

    /**
     * 자본예산 항목 — 투자자산 구분별 예산
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CapitalBudgetItem")
    public static class CapitalBudgetItem {

        /** 투자자산 구분 코드 */
        @Schema(description = "투자자산 구분 코드")
        private String pulDtt;

        /** 투자자산 구분명 */
        @Schema(description = "투자자산 구분명")
        private String pulDttNm;

        /** 자산 예산 */
        @Schema(description = "자산 예산")
        private BigDecimal assetBg;
    }

    /**
     * 경비 항목 — 비용 구분별 경비
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ExpenseCostItem")
    public static class ExpenseCostItem {

        /** 중복업무 구분 코드 */
        @Schema(description = "중복업무 구분 코드")
        private String dupIoe;

        /** 중복업무 구분명 */
        @Schema(description = "중복업무 구분명")
        private String dupIoeNm;

        /** 수익/비용 구분 코드 */
        @Schema(description = "수익/비용 구분 코드")
        private String ioeC;

        /** 수익/비용 구분명 */
        @Schema(description = "수익/비용 구분명")
        private String ioeCNm;

        /** 경비 금액 */
        @Schema(description = "경비 금액")
        private BigDecimal costBg;
    }
}
