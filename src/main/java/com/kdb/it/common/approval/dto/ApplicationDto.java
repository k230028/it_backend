package com.kdb.it.common.approval.dto;

import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.repository.ApproverRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 신청서(결재) 관련 DTO 클래스 모음
 *
 * <p>신청서 등록, 결재(단건/일괄), 조회에 사용되는 Request/Response DTO를 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 *
 * <p>포함된 DTO:
 *
 * <ul>
 *   <li>{@link CreateRequest}: 신청서 등록 요청
 *   <li>{@link ApproveRequest}: 단건 결재 요청
 *   <li>{@link BulkApproveRequest}: 일괄 결재 요청
 *   <li>{@link ApprovalItem}: 일괄 결재 요청의 개별 항목
 *   <li>{@link BulkApproveResponse}: 일괄 결재 응답
 *   <li>{@link ApprovalResult}: 일괄 결재 응답의 개별 결과
 *   <li>{@link BulkGetRequest}: 일괄 조회 요청
 *   <li>{@link Response}: 신청서 조회 응답 (결재자 목록 포함)
 *   <li>{@link ApproverResponse}: 결재자 정보 응답
 * </ul>
 */
public class ApplicationDto {

    /**
     * 원본 데이터 연결 항목 DTO
     *
     * <p>단일 신청서가 복수의 원본 레코드(정보화사업, 전산관리비 등)를 연결할 때 {@link CreateRequest#orcItems} 배열의 요소로 사용됩니다.
     */
    @Getter
    @Setter
    @Schema(name = "ApplicationOrcItem", description = "원천 데이터 연결 항목")
    public static class OrcItem {
        /** 기본 생성자 — Jackson 역직렬화용. */
        public OrcItem() {}

        /** 원천 테이블명 (예: "BPROJM"=정보화사업, "BCOSTM"=전산관리비) */
        @Schema(description = "원천 테이블명")
        private String fntTbNm;

        /** 주식별자 컬럼명 (예: 프로젝트관리번호 컬럼명 "PRJ_MNG_NO") */
        @Schema(description = "주식별자 컬럼명")
        private String pkColNm;

        /** 원천테이블 적재일련번호 (nullable, 예: "1") */
        @Schema(description = "원천테이블 적재일련번호")
        private String fntTbCrySno;
    }

    /**
     * 신청서 등록 요청 DTO
     *
     * <p>신규 신청서를 등록할 때 사용합니다. 복수의 원본 데이터 연결 정보({@link OrcItem} 목록)와 신청서 본문, 결재자 목록을 포함합니다.
     *
     * <p>한 번의 상신으로 복수의 원본 레코드(정보화사업, 전산관리비 등)를 하나의 신청서로 묶어 처리합니다. {@code orcItems}의 각 항목에 대해 {@code
     * TPRMPP_CAPPLA} 행이 생성됩니다.
     */
    @Getter
    @Setter
    @Schema(name = "ApplicationCreateRequest")
    public static class CreateRequest {
        /** 기본 생성자 — Jackson 역직렬화용. */
        public CreateRequest() {}

        /** 신청서명 (예: "전산예산 작성") */
        @Schema(description = "신청서명")
        private String apfNm;

        /**
         * 신청서 세부 내용 (JSON 형식)
         *
         * <p>결재선(approvalLine) 정보 및 전체 정보화사업/전산관리비 목록을 포함하는 JSON 문자열. 결재 처리 시 각 결재자의 date 필드가
         * 업데이트됩니다.
         *
         * <p>구조 예시: {@code { "projects": [...], "costs": [...], "approvalLine": {...} }}
         */
        @Schema(description = "신청서세부내용 (JSON)")
        private String apfDtlCone;

        /**
         * 원본 데이터 연결 항목 목록 (복수 원본 지원)
         *
         * <p>각 항목마다 {@code TPRMPP_CAPPLA} 행이 생성됩니다. null 또는 빈 리스트인 경우 Cappla를 저장하지 않습니다.
         */
        @Schema(description = "원본 데이터 연결 항목 목록")
        private List<OrcItem> orcItems;

        /** 신청자 사원번호 (현재 로그인한 사용자) */
        @Schema(description = "신청 사원번호")
        private String rqsEno;

        /** 신청의견 (신청 사유, 비고 등) */
        @Schema(description = "신청의견")
        private String rqsOpnn;

        /**
         * 결재자 사원번호 목록 (순서대로)
         *
         * <p>순서대로 결재선을 구성합니다. 동일 사원번호가 연속으로 올 수 있으며, 이 경우 해당 결재자가 한 번 승인하면 연속 항목 모두 승인됩니다.
         */
        @Schema(description = "결재자 사원번호 목록 (순서대로)")
        private List<String> approverEnos;
    }

    /**
     * 단건 결재 요청 DTO
     *
     * <p>특정 신청서에 대해 결재자가 승인 또는 반려할 때 사용합니다.
     */
    @Getter
    @Setter
    @Schema(name = "ApplicationApproveRequest")
    public static class ApproveRequest {
        /** 기본 생성자 — Jackson 역직렬화용. */
        public ApproveRequest() {}

        /**
         * 결재자 사원번호
         *
         * <p>실제 서비스에서는 JWT 토큰에서 추출한 현재 사용자 사번을 사용해야 합니다. 현재는 요청 Body에서 받도록 구현되어 있습니다.
         */
        @Schema(description = "결재자 사원번호 (실제로는 세션에서 가져와야 함)")
        private String dcdEno;

        /** 결재의견 (승인/반려 사유) */
        @Schema(description = "결재의견")
        private String dcdOpnn;

        /** 결재상태 (허용값: "승인" 또는 "반려") */
        @Schema(description = "결재상태 (승인/반려)")
        private String dcdSts;
    }

    /**
     * 신청서 회수 요청 DTO
     *
     * <p>신청자가 결재 진행 중인 신청서를 회수(상신 취소)할 때 사용합니다.
     */
    @Getter
    @Setter
    @Schema(name = "RecallRequest", description = "신청서 회수 요청")
    public static class RecallRequest {
        /** 기본 생성자 — Jackson 역직렬화용. */
        public RecallRequest() {}

        /** 회수 사유 (필수, 최대 1000자) */
        @NotBlank
        @Size(max = 1000)
        @Schema(description = "회수 사유 (필수)", example = "결재선 오기재로 인한 회수")
        private String recallOpnn;
    }

    /**
     * 일괄 결재 요청 DTO
     *
     * <p>여러 신청서를 한 번에 결재 처리할 때 사용합니다. 하나라도 실패하면 전체 트랜잭션이 롤백됩니다.
     */
    @Getter
    @Setter
    @Schema(name = "ApplicationBulkApproveRequest", description = "일괄 승인 요청")
    public static class BulkApproveRequest {
        /** 기본 생성자 — Jackson 역직렬화용. */
        public BulkApproveRequest() {}

        /** 결재 처리할 신청서 목록 (각 항목에 결재자 정보 포함) */
        @Schema(description = "승인할 신청서 목록")
        private List<ApprovalItem> approvals;
    }

    /**
     * 일괄 결재 요청의 개별 항목 DTO
     *
     * <p>일괄 결재 요청({@link BulkApproveRequest})의 각 신청서별 결재 정보입니다.
     */
    @Getter
    @Setter
    @Schema(name = "ApplicationApprovalItem", description = "개별 신청서 승인 정보")
    public static class ApprovalItem {
        /** 기본 생성자 — Jackson 역직렬화용. */
        public ApprovalItem() {}

        /** 결재할 신청관리번호 */
        @Schema(description = "신청관리번호")
        private String apfMngNo;

        /** 결재자 사원번호 */
        @Schema(description = "결재자 사원번호")
        private String dcdEno;

        /** 결재의견 */
        @Schema(description = "결재의견")
        private String dcdOpnn;

        /** 결재상태 (허용값: "승인" 또는 "반려") */
        @Schema(description = "결재상태 (승인/반려)")
        private String dcdSts;
    }

    /**
     * 일괄 결재 응답 DTO
     *
     * <p>일괄 결재 처리 후 전체/성공/실패 건수와 개별 결과 목록을 반환합니다.
     */
    @Getter
    @Builder
    @Schema(name = "ApplicationBulkApproveResponse", description = "일괄 승인 응답")
    public static class BulkApproveResponse {
        /** 전체 처리 건수 (요청된 신청서 수) */
        @Schema(description = "전체 처리 건수")
        private int totalCount;

        /** 성공 건수 */
        @Schema(description = "성공 건수")
        private int successCount;

        /** 실패 건수 (전체 롤백 방식이므로 실제로는 0 또는 전체) */
        @Schema(description = "실패 건수")
        private int failureCount;

        /** 개별 승인 결과 목록 */
        @Schema(description = "개별 승인 결과 목록")
        private List<ApprovalResult> results;
    }

    /**
     * 일괄 결재 응답의 개별 결과 DTO
     *
     * <p>각 신청서별 결재 처리 결과입니다.
     */
    @Getter
    @Builder
    @Schema(name = "ApplicationApprovalResult", description = "개별 승인 결과")
    public static class ApprovalResult {
        /** 처리된 신청관리번호 */
        @Schema(description = "신청관리번호")
        private String apfMngNo;

        /** 처리 성공 여부 */
        @Schema(description = "성공 여부")
        private boolean success;

        /** 처리 결과 메시지 (예: "처리 완료") */
        @Schema(description = "결과 메시지")
        private String message;
    }

    /**
     * 일괄 조회 요청 DTO
     *
     * <p>여러 신청관리번호를 한 번에 조회할 때 사용합니다.
     */
    @Getter
    @Setter
    @Schema(name = "ApplicationBulkGetRequest", description = "일괄 조회 요청")
    public static class BulkGetRequest {
        /** 기본 생성자 — Jackson 역직렬화용. */
        public BulkGetRequest() {}

        /** 조회할 신청관리번호 목록 (예: ["APF_202600000001", "APF_202600000002"]) */
        @Schema(description = "조회할 신청관리번호 목록")
        private List<String> apfMngNos;
    }

    /**
     * 신청서 일괄 조회 결과 DTO (부분 성공)
     *
     * <p>조회에 성공한 신청서 목록({@code items})과 미존재로 실패한 신청관리번호 목록({@code failedIds})을 함께 반환합니다. 누락 건을 조용히
     * 버리지 않고 호출자에게 노출하기 위함입니다.
     *
     * @param items 조회 성공 항목
     * @param failedIds 조회 실패(미존재) 신청관리번호 목록
     */
    @Schema(name = "ApplicationBulkResponse", description = "신청서 일괄 조회 결과 (부분 성공)")
    public record BulkResponse(
            @Schema(description = "조회 성공 항목") java.util.List<Response> items,
            @Schema(description = "조회 실패(미존재) 신청관리번호 목록") java.util.List<String> failedIds) {}

    /**
     * 신청서 조회 응답 DTO
     *
     * <p>신청서 마스터({@link Capplm}) 정보와 결재자 목록({@link Cdecim})을 하나의 응답으로 반환합니다.
     */
    @Getter
    @Builder
    @Schema(name = "ApplicationResponse")
    public static class Response {
        /** 신청관리번호 (PK, 예: "APF_202600000001") */
        @Schema(description = "신청관리번호")
        private String apfMngNo;

        /** 신청서명 */
        @Schema(description = "신청서명")
        private String apfNm;

        /** 신청서 세부 내용 (JSON 형식, 결재선 정보 포함) */
        @Schema(description = "신청서세부내용")
        private String apfDtlCone;

        /** 신청상태 (예: "결재중", "결재완료", "반려") */
        @Schema(description = "신청상태")
        private String apfSts;

        /** 신청상태코드 (Ccodem cId='APF_STS' 기반 코드값) */
        @Schema(description = "신청상태코드")
        private String apfStsC;

        /** 신청자 사원번호 */
        @Schema(description = "신청자 사원번호")
        private String rqsEno;

        /** 신청자명 */
        @Schema(description = "신청자명")
        private String rqsNm;

        /** 신청부서코드 */
        @Schema(description = "신청부서코드")
        private String rqsBbrC;

        /** 신청부서명 */
        @Schema(description = "신청부서명")
        private String rqsBbrNm;

        /** 신청일자 */
        @Schema(description = "신청일자")
        private LocalDate rqsDt;

        /** 신청의견 */
        @Schema(description = "신청의견")
        private String rqsOpnn;

        /** 결재자 목록 (순번 순서대로) */
        @Schema(description = "결재자 목록")
        private List<ApproverResponse> approvers;

        /**
         * 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param capplm 신청서 마스터 엔티티
         * @param approvers 결재자 목록 엔티티
         * @return 변환된 응답 DTO
         */
        public static Response fromEntity(Capplm capplm, List<Cdecim> approvers) {
            return fromEntity(capplm, approvers, null);
        }

        /**
         * 엔티티와 신청자명을 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param capplm 신청서 마스터 엔티티
         * @param approvers 결재자 목록 엔티티
         * @param requesterNm 신청자명
         * @return 변환된 응답 DTO
         */
        public static Response fromEntity(
                Capplm capplm, List<Cdecim> approvers, String requesterNm) {
            return fromEntity(capplm, approvers, requesterNm, null);
        }

        /**
         * 엔티티와 신청자/신청부서명을 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param capplm 신청서 마스터 엔티티
         * @param approvers 결재자 목록 엔티티
         * @param requesterNm 신청자명
         * @param requesterBbrNm 신청부서명
         * @return 변환된 응답 DTO
         */
        public static Response fromEntity(
                Capplm capplm, List<Cdecim> approvers, String requesterNm, String requesterBbrNm) {
            return Response.builder()
                    .apfMngNo(capplm.getApfMngNo()) // 신청관리번호
                    .apfNm(capplm.getDcdReqTtl()) // 신청서명(결재요청제목에서 파생)
                    .apfDtlCone(capplm.getDcdReqInf()) // 신청서세부내용(결재요청정보에서 파생)
                    .apfSts(
                            capplm.getItPtlApfPrgStsC() == null
                                    ? null
                                    : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(
                                                    capplm.getItPtlApfPrgStsC())
                                            .label()) // 신청상태(라벨, 코드에서 파생)
                    .apfStsC(capplm.getItPtlApfPrgStsC()) // 신청상태코드
                    .rqsEno(capplm.getDcdReqUsid()) // 신청자 사원번호(결재요청사용자ID에서 파생)
                    .rqsNm(requesterNm) // 신청자명
                    .rqsBbrC(capplm.getDcdReqBbrC()) // 신청부서코드
                    .rqsBbrNm(requesterBbrNm) // 신청부서명
                    .rqsDt(capplm.getDcdReqDtm()) // 신청일자(결재요청일시에서 파생)
                    .rqsOpnn(capplm.getRgprDcdReqCone()) // 신청의견(등록자결재요청내용에서 파생)
                    .approvers(
                            approvers.stream()
                                    .map(ApproverResponse::fromEntity) // 각 결재자 엔티티를 DTO로 변환
                                    .toList())
                    .build();
        }

        /**
         * 신청서 엔티티와 결재선 read view를 응답 DTO로 변환합니다.
         *
         * @param capplm 신청서 마스터 엔티티
         * @param approvers 결재 순번 오름차순 read view 목록
         * @param requesterNm 신청자명
         * @param requesterBbrNm 신청부서명
         * @return 변환된 응답 DTO
         */
        public static Response fromReadViews(
                Capplm capplm,
                List<ApproverRepository.ApproverReadView> approvers,
                String requesterNm,
                String requesterBbrNm) {
            return Response.builder()
                    .apfMngNo(capplm.getApfMngNo())
                    .apfNm(capplm.getDcdReqTtl())
                    .apfDtlCone(capplm.getDcdReqInf())
                    .apfSts(
                            capplm.getItPtlApfPrgStsC() == null
                                    ? null
                                    : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(
                                                    capplm.getItPtlApfPrgStsC())
                                            .label())
                    .apfStsC(capplm.getItPtlApfPrgStsC())
                    .rqsEno(capplm.getDcdReqUsid())
                    .rqsNm(requesterNm)
                    .rqsBbrC(capplm.getDcdReqBbrC())
                    .rqsBbrNm(requesterBbrNm)
                    .rqsDt(capplm.getDcdReqDtm())
                    .rqsOpnn(capplm.getRgprDcdReqCone())
                    .approvers(approvers.stream().map(ApproverResponse::fromReadView).toList())
                    .build();
        }
    }

    /**
     * 신청서 세부내용(APF_DTL_CONE) 조회 응답 DTO
     *
     * <p>신청관리번호({@code apfMngNo})와 대응하는 세부내용 JSON 문자열만 반환합니다.
     */
    @Getter
    @Builder
    @Schema(name = "ApplicationApfDtlConeResponse", description = "신청서 세부내용 조회 응답")
    public static class ApfDtlConeResponse {

        /** 신청관리번호 (요청한 apfMngNo 그대로 반환) */
        @Schema(description = "신청관리번호")
        private String apfMngNo;

        /**
         * 신청서 세부내용 (APF_DTL_CONE)
         *
         * <p>신청 시 저장한 JSON 문자열 그대로 반환합니다.
         */
        @Schema(description = "신청서 세부내용 (JSON 문자열)")
        private String apfDtlCone;

        /**
         * {@link Capplm} 엔티티로부터 DTO 생성
         *
         * @param capplm 신청서 마스터 엔티티
         * @return 변환된 ApfDtlConeResponse
         */
        public static ApfDtlConeResponse fromEntity(Capplm capplm) {
            return ApfDtlConeResponse.builder()
                    .apfMngNo(capplm.getApfMngNo()) // 신청관리번호
                    .apfDtlCone(capplm.getDcdReqInf()) // 세부내용(결재요청정보에서 파생)
                    .build();
        }
    }

    /**
     * 미결재(미상신) 건수 응답 DTO
     *
     * <p>사이드바 결재 상신 배지 등에서 사용합니다. 정보화사업(BPROJM)과 전산업무비(BCOSTM) 중 결재 신청이 없는(CAPPLA 미연결) 건수를 집계합니다.
     *
     * <p>전체 건수 집계에 최적화되어 있으며, 상세 목록 조회가 필요하지 않은 경우 사용합니다.
     */
    @Getter
    @Builder
    @Schema(name = "ApplicationPendingCountResponse", description = "미결재(미상신) 건수 응답")
    public static class PendingCountResponse {
        /** 미상신 정보화사업 건수 */
        @Schema(description = "미상신 정보화사업 건수")
        private long projectCount;

        /** 미상신 전산업무비 건수 */
        @Schema(description = "미상신 전산업무비 건수")
        private long costCount;

        /** 전체 미상신 건수 (projectCount + costCount) */
        @Schema(description = "전체 미상신 건수")
        private long totalCount;
    }

    /** 전자결재 대시보드 응답 DTO */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ApprovalDashboardResponse", description = "전자결재 대시보드 응답")
    public static class DashboardResponse {
        @Schema(description = "결재 대기 수 (본인)")
        private int pendingCount;

        @Schema(description = "내가 기안한 진행 중 수")
        private int inProgressCount;

        @Schema(description = "이번달 부서 완료 수")
        private int monthlyCompletedCount;

        @Schema(description = "내 반려 수")
        private int rejectedCount;

        @Schema(description = "최근 6개월 월별 처리 현황")
        private List<MonthlyCount> monthlyTrend;

        @Schema(description = "내 결재 대기 목록 (최대 3건)")
        private List<PendingItem> pendingList;
    }

    /** 월별 건수 DTO (결재 대시보드) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ApprovalMonthlyCount", description = "월별 결재 처리 건수")
    public static class MonthlyCount {
        @Schema(description = "년월 (YYYY-MM)")
        private String month;

        @Schema(description = "건수")
        private int count;
    }

    /** 결재 대기 항목 DTO */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ApprovalPendingItem", description = "결재 대기 항목")
    public static class PendingItem {
        @Schema(description = "신청서관리번호")
        private String apfMngNo;

        @Schema(description = "신청서명")
        private String title;

        @Schema(description = "신청자명")
        private String requesterName;

        @Schema(description = "신청일자 (YYYY-MM-DD)")
        private String requestedAt;

        @Schema(description = "긴급여부: urgent(3일 초과) | normal")
        private String urgency;
    }

    /** 사이드바 배지 건수 응답 DTO */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ApprovalBadgeCountResponse", description = "결재 사이드바 배지 건수 응답")
    public static class ApprovalBadgeCountResponse {
        @Schema(description = "결재 대기 수")
        private int pendingCount;

        @Schema(description = "기안 진행 중 수")
        private int inProgressCount;
    }

    /**
     * 결재자 정보 응답 DTO
     *
     * <p>결재자({@link Cdecim}) 한 명의 결재 정보를 담습니다.
     */
    @Getter
    @Builder
    @Schema(name = "ApplicationApproverResponse")
    public static class ApproverResponse {
        /** 결재순번 (1부터 시작, 순차 결재 순서) */
        @Schema(description = "결재순번")
        private Integer dcdSqn;

        /** 결재자 사원번호 */
        @Schema(description = "결재자 사원번호")
        private String dcdEno;

        /** 결재유형 (예: "결재", null이면 미결재) */
        @Schema(description = "결재유형")
        private String dcdTp;

        /** 결재일자 (결재 처리된 날짜) */
        @Schema(description = "결재일자")
        private LocalDate dcdDt;

        /** 결재의견 */
        @Schema(description = "결재의견")
        private String dcdOpnn;

        /** 결재상태 (예: "승인", "반려", null이면 미결재) */
        @Schema(description = "결재상태")
        private String dcdSts;

        /** 최종결재자여부 ("Y"/"N", 프론트엔드 회수(중간결재자 회수) 분기 판단에 사용) */
        @Schema(description = "최종결재자여부 (Y/N)")
        private String lstDcdYn;

        /**
         * 결재 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param cdecim 결재 엔티티
         * @return 변환된 결재자 응답 DTO
         */
        public static ApproverResponse fromEntity(Cdecim cdecim) {
            return ApproverResponse.builder()
                    .dcdSqn(cdecim.getDcrSqnSno()) // 결재순번
                    .dcdEno(cdecim.getDcrEno()) // 결재자 사원번호
                    // 결재유형: 미결재(001) 또는 null이면 null, 그 외는 "결재"로 표시
                    .dcdTp(
                            cdecim.getItPtlDcdStsC() == null
                                            || DecisionStatus.isPendingCode(
                                                    cdecim.getItPtlDcdStsC())
                                    ? null
                                    : "결재")
                    .dcdDt(cdecim.getDcdDtm()) // 결재일자
                    .dcdOpnn(cdecim.getDcrOpnnCone()) // 결재의견
                    // 결재상태: 코드 → 라벨 변환 (미결재/null이면 null)
                    .dcdSts(
                            cdecim.getItPtlDcdStsC() == null
                                            || DecisionStatus.isPendingCode(
                                                    cdecim.getItPtlDcdStsC())
                                    ? null
                                    : DecisionStatus.ofCode(cdecim.getItPtlDcdStsC()).label())
                    .lstDcdYn(cdecim.getLstDcdYn()) // 최종결재자여부
                    .build();
        }

        /**
         * 결재선 read view를 결재자 응답 DTO로 변환합니다.
         *
         * @param view 결재선 read view
         * @return 변환된 결재자 응답 DTO
         */
        public static ApproverResponse fromReadView(ApproverRepository.ApproverReadView view) {
            String status = view.getItPtlDcdStsC();
            boolean pending = status == null || DecisionStatus.isPendingCode(status);
            return ApproverResponse.builder()
                    .dcdSqn(view.getDcrSqnSno())
                    .dcdEno(view.getDcrEno())
                    .dcdTp(pending ? null : "결재")
                    .dcdDt(view.getDcdDtm())
                    .dcdOpnn(view.getDcrOpnnCone())
                    .dcdSts(pending ? null : DecisionStatus.ofCode(status).label())
                    .lstDcdYn(view.getLstDcdYn())
                    .build();
        }
    }
}
