package com.kdb.it.domain.budget.status.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 예산 현황 DTO
 *
 * <p>
 * 예산 현황 화면의 3개 탭(정보화사업/전산업무비/경상사업) 각각에 대한 응답 DTO를 정의합니다.
 * 편성요청 금액(req*)과 조정(편성) 금액(adj*)을 병렬로 포함합니다.
 * </p>
 *
 * // Design Ref: §3.3 — BudgetStatusDto 설계
 */
public class BudgetStatusDto {

    /**
     * 정보화사업 예산 현황 응답 DTO
     *
     * <p>BPROJM + BITEMM(품목구분별 피벗) + BBUGTM(비목별 피벗) 조인 결과</p>
     */
    @Schema(name = "BudgetStatusProjectResponse", description = "예산 현황 - 정보화사업 응답")
    public record ProjectResponse(
            String prjMngNo,
            String prjTp,
            String pulDtt,
            String prjNm,
            String prjDes,
            String svnHdq,
            String svnDpm,
            String svnDpmNm,
            String svnDpmTlr,
            String svnDpmTlrNm,
            String svnDpmCgpr,
            String svnDpmCgprNm,
            String itDpm,
            String itDpmNm,
            String itDpmTlr,
            String itDpmTlrNm,
            String itDpmCgpr,
            String itDpmCgprNm,
            String prjPulPtt,
            LocalDate sttDt,
            LocalDate endDt,
            String rprSts,
            String rprStsNm,
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
            BigDecimal adjDevBg,
            BigDecimal adjMachBg,
            BigDecimal adjIntanBg,
            BigDecimal adjAssetBg,
            BigDecimal adjRentBg,
            BigDecimal adjTravelBg,
            BigDecimal adjServiceBg,
            BigDecimal adjMiscBg,
            BigDecimal adjCostBg,
            BigDecimal adjTotalBg
    ) {}

    /**
     * 전산업무비 예산 현황 응답 DTO
     *
     * <p>BCOSTM + BBUGTM 조인 결과 (일반관리비만)</p>
     */
    @Schema(name = "BudgetStatusCostResponse", description = "예산 현황 - 전산업무비 응답")
    public record CostResponse(
            String itMngcNo,
            String pulDtt,
            String abusC,
            String ioeC,
            String ioeCNm,
            String biceDpm,
            String biceDpmNm,
            String biceTem,
            String biceTemNm,
            String cttNm,
            String cttOpp,
            String infPrtYn,
            String itMngcTp,
            // 편성요청 금액
            BigDecimal reqRentBg,
            BigDecimal reqTravelBg,
            BigDecimal reqServiceBg,
            BigDecimal reqMiscBg,
            BigDecimal reqTotalBg,
            // 조정(편성) 금액
            BigDecimal adjRentBg,
            BigDecimal adjTravelBg,
            BigDecimal adjServiceBg,
            BigDecimal adjMiscBg,
            BigDecimal adjTotalBg
    ) {}

    /**
     * 경상사업 예산 현황 응답 DTO
     *
     * <p>BPROJM(ORN_YN='Y') + BITEMM 조인 (기계장치/기타무형자산 분리)</p>
     */
    @Schema(name = "BudgetStatusOrdinaryResponse", description = "예산 현황 - 경상사업 응답")
    public record OrdinaryResponse(
            String prjMngNo,
            String pulDtt,
            String prjNm,
            String prjDes,
            // 기계장치
            String machCur,
            BigDecimal machQtt,
            BigDecimal machUnitPrice,
            BigDecimal machAmt,
            BigDecimal machAmtKrw,
            // 기타무형자산
            String intanCur,
            BigDecimal intanQtt,
            BigDecimal intanUnitPrice,
            BigDecimal intanAmt,
            BigDecimal intanAmtKrw
    ) {}
}
