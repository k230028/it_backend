package com.kdb.it.domain.budget.status.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 예산 현황 DTO
 *
 * <p>예산 현황 화면의 3개 탭(정보화사업/전산업무비/경상사업) 각각에 대한 응답 DTO를 정의합니다. 편성요청 금액(req*)과 조정(편성) 금액(adj*)을 병렬로
 * 포함합니다. 예산 현황 탭별 요청과 집계 응답을 정적 중첩 DTO로 묶어 관리합니다.
 */
public class BudgetStatusDto {

    /**
     * 정보화사업 예산 현황 응답 DTO
     *
     * <p>BPROJM + BITEMM(품목구분별 피벗) + BBUGTM(비목별 피벗) 조인 결과
     *
     * @param abusMngNo 사업관리번호
     * @param bzTpC 사업유형코드
     * @param abusTc 사업구분코드
     * @param abusNm 사업명
     * @param abusPulConeInf 사업내용
     * @param prlmHrkOgzCCone 인사상위조직코드내용
     * @param svnDpmC 주관부서코드
     * @param svnDpmCNm 주관부서명
     * @param tlrUsid 주관부서 팀장 사번
     * @param tlrUsidNm 주관부서 팀장명
     * @param usid 주관부서 담당자 사번
     * @param usidNm 주관부서 담당자명
     * @param dvmDpmC 개발부서코드
     * @param dvmDpmCNm 개발부서명
     * @param dvmTlrUsid 개발부서 팀장 사번
     * @param dvmTlrUsidNm 개발부서 팀장명
     * @param dvmUsid 개발부서 담당자 사번
     * @param dvmUsidNm 개발부서 담당자명
     * @param exePttYn 프로젝트추진가능성 코드
     * @param sttDt 사업시작일
     * @param endDt 사업종료일
     * @param rprSts 보고상태코드
     * @param rprStsNm 보고상태명
     * @param edrt 전결권
     * @param reqDevBg 개발비 편성요청액
     * @param reqMachBg 기계장치 편성요청액
     * @param reqIntanBg 기타무형자산 편성요청액
     * @param reqAssetBg 자본예산 편성요청액 합계
     * @param reqRentBg 임차료 편성요청액
     * @param reqTravelBg 여비교통비 편성요청액
     * @param reqServiceBg 용역비 편성요청액
     * @param reqMiscBg 기타관리비 편성요청액
     * @param reqCostBg 일반관리비 편성요청액 합계
     * @param reqTotalBg 전체 편성요청액 합계
     * @param adjDevBg 개발비 조정액
     * @param adjMachBg 기계장치 조정액
     * @param adjIntanBg 기타무형자산 조정액
     * @param adjAssetBg 자본예산 조정액 합계
     * @param adjRentBg 임차료 조정액
     * @param adjTravelBg 여비교통비 조정액
     * @param adjServiceBg 용역비 조정액
     * @param adjMiscBg 기타관리비 조정액
     * @param adjCostBg 일반관리비 조정액 합계
     * @param adjTotalBg 전체 조정액 합계
     */
    @Schema(
            name = "BudgetStatusProjectResponse",
            description = "예산 현황 - 정보화사업 응답",
            requiredProperties = {
                "abusMngNo",
                "bzTpC",
                "abusTc",
                "abusNm",
                "abusPulConeInf",
                "prlmHrkOgzCCone",
                "svnDpmC",
                "svnDpmCNm",
                "tlrUsid",
                "tlrUsidNm",
                "usid",
                "usidNm",
                "dvmDpmC",
                "dvmDpmCNm",
                "dvmTlrUsid",
                "dvmTlrUsidNm",
                "dvmUsid",
                "dvmUsidNm",
                "exePttYn",
                "sttDt",
                "endDt",
                "rprSts",
                "rprStsNm",
                "edrt",
                "reqDevBg",
                "reqMachBg",
                "reqIntanBg",
                "reqAssetBg",
                "reqRentBg",
                "reqTravelBg",
                "reqServiceBg",
                "reqMiscBg",
                "reqCostBg",
                "reqTotalBg",
                "adjDevBg",
                "adjMachBg",
                "adjIntanBg",
                "adjAssetBg",
                "adjRentBg",
                "adjTravelBg",
                "adjServiceBg",
                "adjMiscBg",
                "adjCostBg",
                "adjTotalBg"
            })
    public record ProjectResponse(
            String abusMngNo,
            String bzTpC,
            String abusTc,
            String abusNm,
            String abusPulConeInf,
            String prlmHrkOgzCCone,
            String svnDpmC,
            @Schema(nullable = true) String svnDpmCNm,
            String tlrUsid,
            @Schema(nullable = true) String tlrUsidNm,
            String usid,
            @Schema(nullable = true) String usidNm,
            String dvmDpmC,
            @Schema(nullable = true) String dvmDpmCNm,
            String dvmTlrUsid,
            @Schema(nullable = true) String dvmTlrUsidNm,
            String dvmUsid,
            @Schema(nullable = true) String dvmUsidNm,
            @Schema(nullable = true) String exePttYn,
            LocalDate sttDt,
            LocalDate endDt,
            String rprSts,
            @Schema(nullable = true) String rprStsNm,
            String edrt,
            // 편성요청 금액
            BigDecimal reqDevBg,
            BigDecimal reqMachBg,
            BigDecimal reqIntanBg,
            BigDecimal reqAssetBg,
            BigDecimal reqRentBg,
            BigDecimal reqTravelBg,
            BigDecimal reqServiceBg,
            BigDecimal reqMiscBg,
            BigDecimal reqCostBg,
            BigDecimal reqTotalBg,
            // 조정(편성) 금액
            @Schema(nullable = true) BigDecimal adjDevBg,
            @Schema(nullable = true) BigDecimal adjMachBg,
            @Schema(nullable = true) BigDecimal adjIntanBg,
            @Schema(nullable = true) BigDecimal adjAssetBg,
            @Schema(nullable = true) BigDecimal adjRentBg,
            @Schema(nullable = true) BigDecimal adjTravelBg,
            @Schema(nullable = true) BigDecimal adjServiceBg,
            @Schema(nullable = true) BigDecimal adjMiscBg,
            @Schema(nullable = true) BigDecimal adjCostBg,
            @Schema(nullable = true) BigDecimal adjTotalBg) {}

    /**
     * 전산업무비 예산 현황 응답 DTO
     *
     * <p>BCOSTM + BBUGTM 조인 결과 (일반관리비만)
     *
     * @param costBgNo 전산업무비예산번호
     * @param abusTc 사업구분코드
     * @param bgUntAbusC 예산단위사업코드
     * @param ioeC 비목코드
     * @param ioeCNm 비목명
     * @param costSvnDpmC 담당부서코드
     * @param costSvnDpmNm 담당부서명
     * @param svnTemC 담당팀코드
     * @param svnTemNm 담당팀명
     * @param cttNm 계약명
     * @param cttOppNm 계약상대처명
     * @param infPrtYn 정보보호여부
     * @param tmnYn 단말여부
     * @param reqRentBg 임차료 편성요청액
     * @param reqTravelBg 여비교통비 편성요청액
     * @param reqServiceBg 용역비 편성요청액
     * @param reqMiscBg 기타관리비 편성요청액
     * @param reqTotalBg 편성요청액 합계
     * @param adjRentBg 임차료 조정액
     * @param adjTravelBg 여비교통비 조정액
     * @param adjServiceBg 용역비 조정액
     * @param adjMiscBg 기타관리비 조정액
     * @param adjTotalBg 조정액 합계
     */
    @Schema(
            name = "BudgetStatusCostResponse",
            description = "예산 현황 - 전산업무비 응답",
            requiredProperties = {
                "costBgNo",
                "abusTc",
                "bgUntAbusC",
                "ioeC",
                "ioeCNm",
                "costSvnDpmC",
                "costSvnDpmNm",
                "svnTemC",
                "svnTemNm",
                "cttNm",
                "cttOppNm",
                "infPrtYn",
                "tmnYn",
                "reqRentBg",
                "reqTravelBg",
                "reqServiceBg",
                "reqMiscBg",
                "reqTotalBg",
                "adjRentBg",
                "adjTravelBg",
                "adjServiceBg",
                "adjMiscBg",
                "adjTotalBg"
            })
    public record CostResponse(
            String costBgNo,
            String abusTc,
            String bgUntAbusC,
            String ioeC,
            @Schema(nullable = true) String ioeCNm,
            String costSvnDpmC,
            @Schema(nullable = true) String costSvnDpmNm,
            String svnTemC,
            @Schema(nullable = true) String svnTemNm,
            String cttNm,
            String cttOppNm,
            String infPrtYn,
            String tmnYn,
            // 편성요청 금액
            BigDecimal reqRentBg,
            BigDecimal reqTravelBg,
            BigDecimal reqServiceBg,
            BigDecimal reqMiscBg,
            BigDecimal reqTotalBg,
            // 조정(편성) 금액
            @Schema(nullable = true) BigDecimal adjRentBg,
            @Schema(nullable = true) BigDecimal adjTravelBg,
            @Schema(nullable = true) BigDecimal adjServiceBg,
            @Schema(nullable = true) BigDecimal adjMiscBg,
            @Schema(nullable = true) BigDecimal adjTotalBg) {}

    /**
     * 경상사업 예산 현황 응답 DTO
     *
     * <p>BPROJM(ODN_YN='Y') + BITEMM 조인 (기계장치/기타무형자산 분리)
     *
     * @param abusMngNo 사업관리번호
     * @param abusTc 사업구분코드
     * @param abusNm 사업명
     * @param abusPulConeInf 사업내용
     * @param machCur 기계장치 통화코드
     * @param machQtt 기계장치 수량
     * @param machUnitPrice 기계장치 단가
     * @param machAmt 기계장치 금액
     * @param machAmtKrw 기계장치 원화환산금액
     * @param intanCur 기타무형자산 통화코드
     * @param intanQtt 기타무형자산 수량
     * @param intanUnitPrice 기타무형자산 단가
     * @param intanAmt 기타무형자산 금액
     * @param intanAmtKrw 기타무형자산 원화환산금액
     */
    @Schema(
            name = "BudgetStatusOrdinaryResponse",
            description = "예산 현황 - 경상사업 응답",
            requiredProperties = {
                "abusMngNo", "abusTc", "abusNm", "abusPulConeInf", "machCur", "machQtt",
                "machUnitPrice", "machAmt", "machAmtKrw", "intanCur", "intanQtt", "intanUnitPrice",
                "intanAmt", "intanAmtKrw"
            })
    public record OrdinaryResponse(
            String abusMngNo,
            String abusTc,
            String abusNm,
            String abusPulConeInf,
            // 기계장치
            @Schema(nullable = true) String machCur,
            BigDecimal machQtt,
            BigDecimal machUnitPrice,
            BigDecimal machAmt,
            BigDecimal machAmtKrw,
            // 기타무형자산
            @Schema(nullable = true) String intanCur,
            BigDecimal intanQtt,
            BigDecimal intanUnitPrice,
            BigDecimal intanAmt,
            BigDecimal intanAmtKrw) {}

    /**
     * Tiptap 변수 해석용 집계 금액 DTO
     *
     * <p>편성요청액과 편성액 합계를 원(KRW) 단위 정수로 표현합니다. 데이터가 없으면 두 값 모두 {@code null}이 될 수 있습니다.
     *
     * @param requestSum 편성요청액 합계 (원). 데이터 없으면 null
     * @param allocatedSum 편성액 합계 (원). 데이터 없으면 null
     */
    public record AggregatedAmount(Long requestSum, Long allocatedSum) {}
}
