package com.kdb.it.domain.budget.status.repository;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.QCcodem;
import com.kdb.it.common.iam.entity.QCorgnI;
import com.kdb.it.common.util.UserNameResolver;
import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.project.entity.QBitemm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.kdb.it.domain.budget.status.dto.BudgetStatusDto;
import com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount;
import com.kdb.it.domain.budget.work.entity.QBbugtm;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 예산 현황 QueryDSL 쿼리 구현체
 *
 * <p>3개 탭(정보화사업/전산업무비/경상사업)별 QueryDSL 피벗 쿼리를 구현합니다. CASE WHEN + SUM + GROUP BY 패턴으로 DB 레벨에서 피벗 처리하여
 * 단일 쿼리로 정제된 데이터를 반환합니다. QueryDSL 집계 결과를 탭별 응답 DTO로 변환합니다.
 */
@Repository
@RequiredArgsConstructor
public class BudgetStatusQueryRepositoryImpl implements BudgetStatusQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** IOE 공통코드 코드ID */
    private static final String C_ID_IOE = CommonCodeGroups.IOE;

    /** 편성비목 코드타입 상수 */
    private static final String CTP_DEV = "IOE_DVC"; // 개발비

    private static final String CTP_MACH = "IOE_HW"; // 기계장치
    private static final String CTP_INTAN = "IOE_SW"; // 기타무형자산
    private static final String CTP_RENT = "IOE_LEAFE"; // 전산임차료
    private static final String CTP_TRAVEL = "IOE_XPN"; // 전산여비
    private static final String CTP_SERVICE = "IOE_SEVS"; // 전산용역비
    private static final String CTP_MISC = "IOE_IDR"; // 전산제비

    /**
     * 정보화사업 예산 현황 조회
     *
     * <p>BPROJM(경상사업 제외) LEFT JOIN BITEMM(품목구분별 피벗) LEFT JOIN BBUGTM(비목별 피벗) GROUP BY 프로젝트 기본정보로 피벗
     * 집계 후 소계/합계를 후계산합니다.
     *
     * @param bgYy 예산년도
     * @return 정보화사업별 편성요청/조정 금액 목록
     */
    @Override
    public List<BudgetStatusDto.ProjectResponse> findProjectStatus(String bgYy) {
        QBprojm p = QBprojm.bprojm;
        QBitemm i = QBitemm.bitemm;
        QBbugtm b = new QBbugtm("b");
        QCcodem itemCode = new QCcodem("itemCode");
        QCcodem budgetCode = new QCcodem("budgetCode");
        QCcodem rprStsCode = new QCcodem("rprStsCode");
        QCorgnI svnOrg = new QCorgnI("svnOrg"); // 주관부서 조직 조인용
        QCorgnI itOrg = new QCorgnI("itOrg"); // IT담당부서 조직 조인용

        // 담당자 행번 → 이름 변환용 스칼라 서브쿼리 (JPQL 엔티티명 사용)
        StringExpression svnDpmTlrNm =
                Expressions.stringTemplate(
                        "(SELECT u.usrNm FROM CuserI u WHERE u.eno = {0})", p.tlrUsid);
        StringExpression svnDpmCgprNm =
                Expressions.stringTemplate(
                        "(SELECT u.usrNm FROM CuserI u WHERE u.eno = {0})", p.usid);
        StringExpression itDpmTlrNm =
                Expressions.stringTemplate(
                        "(SELECT u.usrNm FROM CuserI u WHERE u.eno = {0})", p.dvmTlrUsid);
        StringExpression itDpmCgprNm =
                Expressions.stringTemplate(
                        "(SELECT u.usrNm FROM CuserI u WHERE u.eno = {0})", p.dvmUsid);

        // 편성요청 금액: BITEMM의 저장 원화금액(amt)을 품목구분별로 피벗
        NumberExpression<BigDecimal> reqDev = sumItemAmtByCTp(itemCode.cTp, i, CTP_DEV);
        NumberExpression<BigDecimal> reqMach = sumItemAmtByCTp(itemCode.cTp, i, CTP_MACH);
        NumberExpression<BigDecimal> reqIntan = sumItemAmtByCTp(itemCode.cTp, i, CTP_INTAN);
        NumberExpression<BigDecimal> reqRent = sumItemAmtByCTp(itemCode.cTp, i, CTP_RENT);
        NumberExpression<BigDecimal> reqTravel = sumItemAmtByCTp(itemCode.cTp, i, CTP_TRAVEL);
        NumberExpression<BigDecimal> reqService = sumItemAmtByCTp(itemCode.cTp, i, CTP_SERVICE);
        NumberExpression<BigDecimal> reqMisc = sumItemAmtByCTp(itemCode.cTp, i, CTP_MISC);

        // 조정(편성) 금액: BBUGTM의 DUP_BG를 비목코드별로 피벗
        NumberExpression<BigDecimal> adjDev = sumDupBgByCTp(budgetCode.cTp, b, CTP_DEV);
        NumberExpression<BigDecimal> adjMach = sumDupBgByCTp(budgetCode.cTp, b, CTP_MACH);
        NumberExpression<BigDecimal> adjIntan = sumDupBgByCTp(budgetCode.cTp, b, CTP_INTAN);
        NumberExpression<BigDecimal> adjRent = sumDupBgByCTp(budgetCode.cTp, b, CTP_RENT);
        NumberExpression<BigDecimal> adjTravel = sumDupBgByCTp(budgetCode.cTp, b, CTP_TRAVEL);
        NumberExpression<BigDecimal> adjService = sumDupBgByCTp(budgetCode.cTp, b, CTP_SERVICE);
        NumberExpression<BigDecimal> adjMisc = sumDupBgByCTp(budgetCode.cTp, b, CTP_MISC);

        List<Tuple> tuples =
                queryFactory
                        .select(
                                p.abusMngNo,
                                p.bzTpC,
                                p.abusTc,
                                p.abusNm,
                                p.abusPulConeInf,
                                p.prlmHrkOgzCCone,
                                p.svnDpmC,
                                svnOrg.bbrNm,
                                p.tlrUsid,
                                svnDpmTlrNm,
                                p.usid,
                                svnDpmCgprNm,
                                p.dvmDpmC,
                                itOrg.bbrNm,
                                p.dvmTlrUsid,
                                itDpmTlrNm,
                                p.dvmUsid,
                                itDpmCgprNm,
                                p.exePttYn,
                                p.sttDtm,
                                p.endDtm,
                                p.rprStsTc,
                                rprStsCode.cdvaNm,
                                p.edrtTc,
                                reqDev,
                                reqMach,
                                reqIntan,
                                reqRent,
                                reqTravel,
                                reqService,
                                reqMisc,
                                adjDev,
                                adjMach,
                                adjIntan,
                                adjRent,
                                adjTravel,
                                adjService,
                                adjMisc)
                        .from(p)
                        .leftJoin(svnOrg)
                        .on(svnOrg.prlmOgzCCone.eq(p.svnDpmC))
                        .leftJoin(itOrg)
                        .on(itOrg.prlmOgzCCone.eq(p.dvmDpmC))
                        .leftJoin(rprStsCode)
                        .on(
                                rprStsCode.cId.eq(CommonCodeGroups.REPORT_STS),
                                rprStsCode.cdva.eq(p.rprStsTc),
                                codeIsActive(rprStsCode))
                        .leftJoin(i)
                        .on(
                                i.abusMngNo.eq(p.abusMngNo),
                                i.fntTbCrySno.eq(p.sno),
                                i.delYn.eq("N"),
                                i.lstYn.eq("Y"))
                        .leftJoin(itemCode)
                        .on(
                                itemCode.cId.eq(C_ID_IOE),
                                itemCode.cdva.eq(i.ioeC),
                                codeIsActive(itemCode))
                        .leftJoin(b)
                        .on(
                                b.fntTbNm.eq("BITEMM"),
                                b.pkColNm.eq(i.gclMngNo),
                                b.bseYy.eq(bgYy),
                                b.delYn.eq("N"))
                        .leftJoin(budgetCode)
                        .on(
                                budgetCode.cId.eq(C_ID_IOE),
                                budgetCode.cdva.eq(b.ioeC),
                                codeIsActive(budgetCode))
                        .where(p.bseYy.eq(bgYy), p.odnYn.ne("Y"), p.delYn.eq("N"), p.lstYn.eq("Y"))
                        .groupBy(
                                p.abusMngNo,
                                p.sno,
                                p.bzTpC,
                                p.abusTc,
                                p.abusNm,
                                p.abusPulConeInf,
                                p.prlmHrkOgzCCone,
                                p.svnDpmC,
                                svnOrg.bbrNm,
                                p.tlrUsid,
                                p.usid,
                                p.dvmDpmC,
                                itOrg.bbrNm,
                                p.dvmTlrUsid,
                                p.dvmUsid,
                                p.exePttYn,
                                p.sttDtm,
                                p.endDtm,
                                p.rprStsTc,
                                rprStsCode.cdvaNm,
                                p.edrtTc)
                        .orderBy(p.abusMngNo.asc())
                        .fetch();

        return tuples.stream()
                .map(
                        t -> {
                            // 편성요청 소계/합계 계산
                            BigDecimal rDev = nvl(t.get(reqDev));
                            BigDecimal rMach = nvl(t.get(reqMach));
                            BigDecimal rIntan = nvl(t.get(reqIntan));
                            BigDecimal rAsset = rDev.add(rMach).add(rIntan);
                            BigDecimal rRent = nvl(t.get(reqRent));
                            BigDecimal rTravel = nvl(t.get(reqTravel));
                            BigDecimal rService = nvl(t.get(reqService));
                            BigDecimal rMisc = nvl(t.get(reqMisc));
                            BigDecimal rCost = rRent.add(rTravel).add(rService).add(rMisc);
                            BigDecimal rTotal = rAsset.add(rCost);

                            // 조정(편성) 소계/합계 계산
                            BigDecimal aDev = nvl(t.get(adjDev));
                            BigDecimal aMach = nvl(t.get(adjMach));
                            BigDecimal aIntan = nvl(t.get(adjIntan));
                            BigDecimal aAsset = aDev.add(aMach).add(aIntan);
                            BigDecimal aRent = nvl(t.get(adjRent));
                            BigDecimal aTravel = nvl(t.get(adjTravel));
                            BigDecimal aService = nvl(t.get(adjService));
                            BigDecimal aMisc = nvl(t.get(adjMisc));
                            BigDecimal aCost = aRent.add(aTravel).add(aService).add(aMisc);
                            BigDecimal aTotal = aAsset.add(aCost);

                            return new BudgetStatusDto.ProjectResponse(
                                    // abusMngNo, bzTpC, abusTc, abusNm, abusPulConeInf
                                    t.get(p.abusMngNo),
                                    t.get(p.bzTpC),
                                    t.get(p.abusTc),
                                    t.get(p.abusNm),
                                    t.get(p.abusPulConeInf),
                                    // prlmHrkOgzCCone, svnDpmC, svnDpmCNm
                                    t.get(p.prlmHrkOgzCCone),
                                    t.get(p.svnDpmC),
                                    t.get(svnOrg.bbrNm),
                                    // tlrUsid(주관팀장), tlrUsidNm, usid(주관담당자), usidNm
                                    // 담당자 컬럼은 사번 또는 이름을 담으므로 사번 조인이 비면 저장값을 이름으로 쓴다
                                    t.get(p.tlrUsid),
                                    UserNameResolver.resolve(t.get(p.tlrUsid), t.get(svnDpmTlrNm)),
                                    t.get(p.usid),
                                    UserNameResolver.resolve(t.get(p.usid), t.get(svnDpmCgprNm)),
                                    // dvmDpmC, dvmDpmCNm, dvmTlrUsid(IT팀장), dvmTlrUsidNm, dvmUsid,
                                    // dvmUsidNm
                                    t.get(p.dvmDpmC),
                                    t.get(itOrg.bbrNm),
                                    t.get(p.dvmTlrUsid),
                                    UserNameResolver.resolve(
                                            t.get(p.dvmTlrUsid), t.get(itDpmTlrNm)),
                                    t.get(p.dvmUsid),
                                    UserNameResolver.resolve(t.get(p.dvmUsid), t.get(itDpmCgprNm)),
                                    // exePttYn, sttDt, endDt, rprSts, rprStsNm, edrt
                                    t.get(p.exePttYn),
                                    t.get(p.sttDtm),
                                    t.get(p.endDtm),
                                    t.get(p.rprStsTc),
                                    t.get(rprStsCode.cdvaNm),
                                    t.get(p.edrtTc),
                                    rDev,
                                    rMach,
                                    rIntan,
                                    rAsset,
                                    rRent,
                                    rTravel,
                                    rService,
                                    rMisc,
                                    rCost,
                                    rTotal,
                                    aDev,
                                    aMach,
                                    aIntan,
                                    aAsset,
                                    aRent,
                                    aTravel,
                                    aService,
                                    aMisc,
                                    aCost,
                                    aTotal);
                        })
                .toList();
    }

    /**
     * 전산업무비 예산 현황 조회
     *
     * <p>BCOSTM LEFT JOIN BBUGTM 매핑. 전산업무비는 레코드 1건이 1개 비목에 대응하므로 피벗 불필요. 비목코드 접두어로 해당 컬럼에 금액을
     * 배치합니다.
     *
     * @param bgYy 예산년도
     * @return 전산업무비별 편성요청/조정 금액 목록
     */
    @Override
    public List<BudgetStatusDto.CostResponse> findCostStatus(String bgYy) {
        QBcostm c = QBcostm.bcostm;
        QBbugtm b = new QBbugtm("b");
        QCcodem costCode = new QCcodem("costCode");
        // 전년도 편성 조회용 별칭 (동일 IT_MNGC_NO로 전년도 BBUGTM JOIN)
        QBbugtm bPrev = new QBbugtm("bPrev");
        // 전년도 편성 최대 순번 서브쿼리용 별칭
        QBbugtm bMaxPrev = new QBbugtm("bMaxPrev");
        QCorgnI dpmOrg = new QCorgnI("dpmOrg"); // 담당부서 조직 조인용
        QCorgnI temOrg = new QCorgnI("temOrg"); // 담당팀 조직 조인용

        String prevYy = String.valueOf(Integer.parseInt(bgYy) - 1);

        // 전년도 편성금액: 전년도 BBUGTM DUP_BG를 IOE_C 접두어별 분배 (편성 없으면 0)
        NumberExpression<BigDecimal> reqRent =
                caseDupBgByCTp(costCode.cTp, bPrev.bgDupAmt, CTP_RENT);
        NumberExpression<BigDecimal> reqTravel =
                caseDupBgByCTp(costCode.cTp, bPrev.bgDupAmt, CTP_TRAVEL);
        NumberExpression<BigDecimal> reqService =
                caseDupBgByCTp(costCode.cTp, bPrev.bgDupAmt, CTP_SERVICE);
        NumberExpression<BigDecimal> reqMisc =
                caseDupBgByCTp(costCode.cTp, bPrev.bgDupAmt, CTP_MISC);
        NumberExpression<BigDecimal> reqTotal =
                Expressions.numberTemplate(BigDecimal.class, "COALESCE({0}, 0)", bPrev.bgDupAmt);

        // 금년도 조정: 금년도 BBUGTM의 DUP_BG를 IOE_C 접두어별 분배
        NumberExpression<BigDecimal> adjRent = caseDupBgByCTp(costCode.cTp, b.bgDupAmt, CTP_RENT);
        NumberExpression<BigDecimal> adjTravel =
                caseDupBgByCTp(costCode.cTp, b.bgDupAmt, CTP_TRAVEL);
        NumberExpression<BigDecimal> adjService =
                caseDupBgByCTp(costCode.cTp, b.bgDupAmt, CTP_SERVICE);
        NumberExpression<BigDecimal> adjMisc = caseDupBgByCTp(costCode.cTp, b.bgDupAmt, CTP_MISC);
        NumberExpression<BigDecimal> adjTotal =
                Expressions.numberTemplate(BigDecimal.class, "COALESCE({0}, 0)", b.bgDupAmt);

        List<Tuple> tuples =
                queryFactory
                        .select(
                                c.costBgNo,
                                c.abusTc,
                                c.bgUntAbusC,
                                c.ioeC,
                                costCode.cdvaNm,
                                c.costSvnDpmC,
                                dpmOrg.bbrNm,
                                c.svnTemC,
                                temOrg.bbrNm,
                                c.cttNm,
                                c.cttOppNm,
                                c.sectSysUtzYn,
                                c.tmnYn,
                                reqRent,
                                reqTravel,
                                reqService,
                                reqMisc,
                                reqTotal,
                                adjRent,
                                adjTravel,
                                adjService,
                                adjMisc,
                                adjTotal)
                        .from(c)
                        .leftJoin(dpmOrg)
                        .on(dpmOrg.prlmOgzCCone.eq(c.costSvnDpmC))
                        .leftJoin(temOrg)
                        .on(temOrg.prlmOgzCCone.eq(c.svnTemC))
                        .leftJoin(costCode)
                        .on(
                                costCode.cId.eq(C_ID_IOE),
                                costCode.cdva.eq(c.ioeC),
                                codeIsActive(costCode))
                        .leftJoin(b)
                        .on(
                                b.fntTbNm.eq("BCOSTM"),
                                b.pkColNm.eq(c.costBgNo),
                                b.bseYy.eq(bgYy),
                                b.delYn.eq("N"))
                        .leftJoin(bPrev)
                        .on(
                                bPrev.fntTbNm.eq("BCOSTM"),
                                // 계속항목은 cncdRfrNo 기준, 신규항목은 costBgNo 기준으로 전년도 편성 조회
                                Expressions.booleanTemplate(
                                        "COALESCE({0}, {1}) = {2}",
                                        c.cncdRfrNo, c.costBgNo, bPrev.pkColNm),
                                bPrev.bseYy.eq(prevYy),
                                bPrev.delYn.eq("N"),
                                // 동일 관리번호에 여러 편성건이 있을 경우 마지막 편성건(ORC_SNO_VL 최대값)만 선택
                                bPrev.fntTbCrySno.eq(
                                        JPAExpressions.select(bMaxPrev.fntTbCrySno.max())
                                                .from(bMaxPrev)
                                                .where(
                                                        bMaxPrev.fntTbNm.eq("BCOSTM"),
                                                        Expressions.booleanTemplate(
                                                                "COALESCE({0}, {1}) = {2}",
                                                                c.cncdRfrNo,
                                                                c.costBgNo,
                                                                bMaxPrev.pkColNm),
                                                        bMaxPrev.bseYy.eq(prevYy),
                                                        bMaxPrev.delYn.eq("N"))))
                        .where(c.bseYy.eq(bgYy), c.delYn.eq("N"), c.lstYn.eq("Y"))
                        .orderBy(c.costBgNo.asc())
                        .fetch();

        return tuples.stream()
                .map(
                        t ->
                                new BudgetStatusDto.CostResponse(
                                        t.get(c.costBgNo),
                                        t.get(c.abusTc),
                                        t.get(c.bgUntAbusC),
                                        t.get(c.ioeC),
                                        t.get(costCode.cdvaNm),
                                        t.get(c.costSvnDpmC),
                                        t.get(dpmOrg.bbrNm),
                                        t.get(c.svnTemC),
                                        t.get(temOrg.bbrNm),
                                        t.get(c.cttNm),
                                        t.get(c.cttOppNm),
                                        t.get(c.sectSysUtzYn),
                                        t.get(c.tmnYn),
                                        nvl(t.get(reqRent)),
                                        nvl(t.get(reqTravel)),
                                        nvl(t.get(reqService)),
                                        nvl(t.get(reqMisc)),
                                        nvl(t.get(reqTotal)),
                                        nvl(t.get(adjRent)),
                                        nvl(t.get(adjTravel)),
                                        nvl(t.get(adjService)),
                                        nvl(t.get(adjMisc)),
                                        nvl(t.get(adjTotal))))
                .toList();
    }

    /**
     * 경상사업 예산 현황 조회
     *
     * <p>BPROJM(ODN_YN='Y') LEFT JOIN BITEMM으로 기계장치(IOE-238)과 기타무형자산(IOE-239)을 분리하여 조회합니다.
     * 단가(unitPrice = amt / qtt)는 후계산합니다.
     *
     * @param bgYy 예산년도
     * @return 경상사업별 기계장치/기타무형자산 상세 목록
     */
    @Override
    public List<BudgetStatusDto.OrdinaryResponse> findOrdinaryStatus(String bgYy) {
        QBprojm p = QBprojm.bprojm;
        QBitemm i = QBitemm.bitemm;
        QCcodem itemCode = new QCcodem("ordinaryItemCode");

        // 기계장치 (IOE-238)
        StringExpression machCur =
                Expressions.stringTemplate(
                        "MAX(CASE WHEN {0} = {1} THEN {2} END)",
                        itemCode.cTp, Expressions.constant(CTP_MACH), i.curC);
        NumberExpression<BigDecimal> machQtt = sumFieldByCTp(itemCode.cTp, CTP_MACH, i.qty);
        NumberExpression<BigDecimal> machAmt = sumFieldByCTp(itemCode.cTp, CTP_MACH, i.amt);
        NumberExpression<BigDecimal> machAmtKrw = sumItemAmtByCTp(itemCode.cTp, i, CTP_MACH);

        // 기타무형자산 (IOE-239)
        StringExpression intanCur =
                Expressions.stringTemplate(
                        "MAX(CASE WHEN {0} = {1} THEN {2} END)",
                        itemCode.cTp, Expressions.constant(CTP_INTAN), i.curC);
        NumberExpression<BigDecimal> intanQtt = sumFieldByCTp(itemCode.cTp, CTP_INTAN, i.qty);
        NumberExpression<BigDecimal> intanAmt = sumFieldByCTp(itemCode.cTp, CTP_INTAN, i.amt);
        NumberExpression<BigDecimal> intanAmtKrw = sumItemAmtByCTp(itemCode.cTp, i, CTP_INTAN);

        List<Tuple> tuples =
                queryFactory
                        .select(
                                p.abusMngNo,
                                p.abusTc,
                                p.abusNm,
                                p.abusPulConeInf,
                                machCur,
                                machQtt,
                                machAmt,
                                machAmtKrw,
                                intanCur,
                                intanQtt,
                                intanAmt,
                                intanAmtKrw)
                        .from(p)
                        .leftJoin(i)
                        .on(
                                i.abusMngNo.eq(p.abusMngNo),
                                i.fntTbCrySno.eq(p.sno),
                                i.delYn.eq("N"),
                                i.lstYn.eq("Y"))
                        .leftJoin(itemCode)
                        .on(
                                itemCode.cId.eq(C_ID_IOE),
                                itemCode.cdva.eq(i.ioeC),
                                codeIsActive(itemCode))
                        .where(p.bseYy.eq(bgYy), p.odnYn.eq("Y"), p.delYn.eq("N"), p.lstYn.eq("Y"))
                        .groupBy(p.abusMngNo, p.sno, p.abusTc, p.abusNm, p.abusPulConeInf)
                        .orderBy(p.abusMngNo.asc())
                        .fetch();

        return tuples.stream()
                .map(
                        t -> {
                            // 단가 후계산: unitPrice = amt / qtt (0으로 나누기 방지)
                            BigDecimal mQtt = nvl(t.get(machQtt));
                            BigDecimal mAmt = nvl(t.get(machAmt));
                            BigDecimal mUnitPrice =
                                    mQtt.compareTo(BigDecimal.ZERO) > 0
                                            ? mAmt.divide(mQtt, 2, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO;

                            BigDecimal iQtt = nvl(t.get(intanQtt));
                            BigDecimal iAmt = nvl(t.get(intanAmt));
                            BigDecimal iUnitPrice =
                                    iQtt.compareTo(BigDecimal.ZERO) > 0
                                            ? iAmt.divide(iQtt, 2, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO;

                            return new BudgetStatusDto.OrdinaryResponse(
                                    t.get(p.abusMngNo),
                                    t.get(p.abusTc),
                                    t.get(p.abusNm),
                                    t.get(p.abusPulConeInf),
                                    t.get(machCur),
                                    mQtt,
                                    mUnitPrice,
                                    mAmt,
                                    nvl(t.get(machAmtKrw)),
                                    t.get(intanCur),
                                    iQtt,
                                    iUnitPrice,
                                    iAmt,
                                    nvl(t.get(intanAmtKrw)));
                        })
                .toList();
    }

    /** 자본예산(CAP_BUDGET) 대상 비목 cTpC 목록 — 개발비/기계장치/기타무형자산. */
    private static final List<String> CAP_BUDGET_CTP_CODES = List.of(CTP_DEV, CTP_MACH, CTP_INTAN);

    /** 일반관리비(OPEX) 대상 비목 cTpC 목록 — 전산제비/전산용역비/전산여비/전산임차료. */
    private static final List<String> OPEX_CTP_CODES =
            List.of(CTP_MISC, CTP_SERVICE, CTP_TRAVEL, CTP_RENT);

    /**
     * 카테고리·연도 기준 편성요청액·편성액 합계 조회 (Tiptap 변수 해석 전용)
     *
     * <p>카테고리별 SoT (Bitemm 비목구분 {@code Ccodem.C_TP} 기준 필터링):
     *
     * <ul>
     *   <li>{@code IT_BUDGET} → 전체 Bitemm 합계 (정보화사업·경상사업·일반관리비 포함, 비목 필터 없음)
     *   <li>{@code CAP_BUDGET} → {@code Ccodem.cTp ∈ ('IOE_DVC','IOE_HW','IOE_SW')} 자본예산 항목 합계
     *   <li>{@code OPEX} → {@code Ccodem.cTp ∈ ('IOE_IDR','IOE_SEVS','IOE_XPN','IOE_LEAFE')} 일반관리비
     *       항목 합계
     * </ul>
     *
     * 편성요청액은 저장 시점에 원화로 환산된 {@code BITEMM.amt} 합산, 편성액은 {@code BBUGTM.dupBgAmt}({@code
     * orcTb='BITEMM'}) 합산입니다. 두 합계 모두 0이거나 null이면 {@code AggregatedAmount(null, null)}을
     * 반환합니다(MISSING 판정용).
     *
     * @param year 예산년도
     * @param categoryCode 카테고리 코드 ({@code IT_BUDGET} | {@code CAP_BUDGET} | {@code OPEX})
     * @return 편성요청액·편성액 합계 (원 단위)
     */
    @Override
    public AggregatedAmount aggregateByCategory(int year, String categoryCode) {
        String bgYy = String.valueOf(year);
        return switch (categoryCode) {
            case "IT_BUDGET" -> aggregateItemsByCTp(bgYy, null);
            case "CAP_BUDGET" -> aggregateItemsByCTp(bgYy, CAP_BUDGET_CTP_CODES);
            case "OPEX" -> aggregateItemsByCTp(bgYy, OPEX_CTP_CODES);
            default -> new AggregatedAmount(null, null);
        };
    }

    /**
     * 사업·연도 기준 편성요청액·편성액 합계 조회 (Tiptap 변수 해석 전용)
     *
     * <p>특정 {@code PRJ_MNG_NO}의 {@code BITEMM} 금액 합계와 매핑된 {@code BBUGTM} 편성예산 합계를 반환합니다. 두 합계 모두
     * 0이거나 null이면 {@code AggregatedAmount(null, null)}을 반환합니다.
     *
     * @param year 예산년도
     * @param projectCode 정보화사업 관리번호 (예: {@code PRJ-2026-0001})
     * @return 편성요청액·편성액 합계 (원 단위)
     */
    @Override
    public AggregatedAmount aggregateByProject(int year, String projectCode) {
        String bgYy = String.valueOf(year);
        QBprojm p = QBprojm.bprojm;
        QBitemm i = QBitemm.bitemm;
        QBbugtm b = QBbugtm.bbugtm;

        // 편성요청액: BITEMM.amt 합계 — 해당 사업의 최신 버전·미삭제 품목 대상
        BigDecimal requestSum =
                queryFactory
                        .select(
                                Expressions.numberTemplate(
                                        BigDecimal.class, "COALESCE(SUM({0}), 0)", i.amt))
                        .from(p)
                        .join(i)
                        .on(
                                i.abusMngNo.eq(p.abusMngNo),
                                i.fntTbCrySno.eq(p.sno),
                                i.delYn.eq("N"),
                                i.lstYn.eq("Y"))
                        .where(
                                p.abusMngNo.eq(projectCode),
                                p.bseYy.eq(bgYy),
                                p.delYn.eq("N"),
                                p.lstYn.eq("Y"))
                        .fetchOne();

        // 편성액: BBUGTM.dupBgAmt 합계 — 해당 사업의 BITEMM(gclMngNo)을 통해 매핑된 편성예산
        BigDecimal allocatedSum =
                queryFactory
                        .select(b.bgDupAmt.sum().coalesce(BigDecimal.ZERO))
                        .from(b)
                        .join(i)
                        .on(i.gclMngNo.eq(b.pkColNm), i.delYn.eq("N"), i.lstYn.eq("Y"))
                        .where(
                                b.fntTbNm.eq("BITEMM"),
                                b.bseYy.eq(bgYy),
                                b.delYn.eq("N"),
                                i.abusMngNo.eq(projectCode))
                        .fetchOne();

        return toAggregated(requestSum, allocatedSum);
    }

    /**
     * Bitemm 비목구분({@code Ccodem.cTp}) 기준 편성요청액·편성액 합계 집계.
     *
     * <p>Bprojm({@code lstYn='Y'}, {@code delYn='N'}) ⨝ Bitemm({@code lstYn='Y'}, {@code
     * delYn='N'}) ⨝ Ccodem({@code cId='IOE'}, {@code cdva=ioeC})에 대해 {@code cTpCodes}가 비어있지 않으면
     * {@code Ccodem.cTp IN (cTpCodes)} 필터를 추가합니다. {@code ornYn} 필터는 적용하지 않습니다(전체 Bitemm 대상).
     *
     * @param bgYy 예산년도 문자열 (예: "2026")
     * @param cTpCodes 비목 cTpC 화이트리스트. {@code null} 또는 빈 리스트이면 비목 필터 없이 전체 합산.
     */
    private AggregatedAmount aggregateItemsByCTp(String bgYy, List<String> cTpCodes) {
        QBprojm p = QBprojm.bprojm;
        QBitemm i = QBitemm.bitemm;
        QBbugtm b = QBbugtm.bbugtm;
        QCcodem itemCode = new QCcodem("aggItemCode");

        BooleanExpression cTpFilter =
                (cTpCodes == null || cTpCodes.isEmpty()) ? null : itemCode.cTp.in(cTpCodes);

        // 편성요청액: BITEMM.amt 합계 — 최신 버전·미삭제 사업/품목 대상
        BigDecimal requestSum =
                queryFactory
                        .select(
                                Expressions.numberTemplate(
                                        BigDecimal.class, "COALESCE(SUM({0}), 0)", i.amt))
                        .from(p)
                        .join(i)
                        .on(
                                i.abusMngNo.eq(p.abusMngNo),
                                i.fntTbCrySno.eq(p.sno),
                                i.delYn.eq("N"),
                                i.lstYn.eq("Y"))
                        .leftJoin(itemCode)
                        .on(
                                itemCode.cId.eq(C_ID_IOE),
                                itemCode.cdva.eq(i.ioeC),
                                codeIsActive(itemCode))
                        .where(p.bseYy.eq(bgYy), p.delYn.eq("N"), p.lstYn.eq("Y"), cTpFilter)
                        .fetchOne();

        // 편성액: BBUGTM.dupBgAmt 합계 — BITEMM(gclMngNo)을 통해 매핑된 편성예산
        BigDecimal allocatedSum =
                queryFactory
                        .select(b.bgDupAmt.sum().coalesce(BigDecimal.ZERO))
                        .from(b)
                        .join(i)
                        .on(i.gclMngNo.eq(b.pkColNm), i.delYn.eq("N"), i.lstYn.eq("Y"))
                        .join(p)
                        .on(
                                p.abusMngNo.eq(i.abusMngNo),
                                p.sno.eq(i.fntTbCrySno),
                                p.delYn.eq("N"),
                                p.lstYn.eq("Y"))
                        .leftJoin(itemCode)
                        .on(
                                itemCode.cId.eq(C_ID_IOE),
                                itemCode.cdva.eq(i.ioeC),
                                codeIsActive(itemCode))
                        .where(b.fntTbNm.eq("BITEMM"), b.bseYy.eq(bgYy), b.delYn.eq("N"), cTpFilter)
                        .fetchOne();

        return toAggregated(requestSum, allocatedSum);
    }

    /**
     * BigDecimal 합계를 Long으로 변환합니다. 두 값 모두 null 또는 0이면 {@code (null, null)}을 반환하여 MISSING으로 분기되도록
     * 합니다.
     */
    private AggregatedAmount toAggregated(BigDecimal requestSum, BigDecimal allocatedSum) {
        Long request =
                (requestSum == null || requestSum.signum() == 0)
                        ? null
                        : requestSum.longValueExact();
        Long allocated =
                (allocatedSum == null || allocatedSum.signum() == 0)
                        ? null
                        : allocatedSum.longValueExact();
        return new AggregatedAmount(request, allocated);
    }

    // ===== 헬퍼 메서드 =====

    /** 현재 유효한 공통코드만 조인합니다. */
    private BooleanExpression codeIsActive(QCcodem code) {
        // 시작·종료일자는 'YYYYMMDD' 문자열이므로 기준일자도 동일 형식으로 비교
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return code.delYn
                .eq("N")
                .and(code.sttDt.loe(today))
                .and(code.endDt.isNull().or(code.endDt.goe(today)));
    }

    /**
     * BITEMM 품목구분별 저장 원화금액 피벗
     *
     * <p>SUM(CASE WHEN C_TP = 'codeType' THEN amt ELSE 0 END)
     */
    private NumberExpression<BigDecimal> sumItemAmtByCTp(
            StringExpression cTp, QBitemm i, String codeType) {
        return Expressions.numberTemplate(
                BigDecimal.class,
                "COALESCE(SUM(CASE WHEN {0} = {1} THEN {2} ELSE 0 END), 0)",
                cTp,
                Expressions.constant(codeType),
                i.amt);
    }

    /**
     * BBUGTM 비목코드별 편성예산 피벗
     *
     * <p>SUM(CASE WHEN C_TP = 'codeType' THEN dupBgAmt ELSE 0 END)
     */
    private NumberExpression<BigDecimal> sumDupBgByCTp(
            StringExpression cTp, QBbugtm b, String codeType) {
        return Expressions.numberTemplate(
                BigDecimal.class,
                "COALESCE(SUM(CASE WHEN {0} = {1} THEN {2} ELSE 0 END), 0)",
                cTp,
                Expressions.constant(codeType),
                b.bgDupAmt);
    }

    /**
     * 비목코드 접두어별 편성예산 분배 (비집계, 전산업무비용)
     *
     * <p>CASE WHEN C_TP = 'codeType' THEN COALESCE(dupBgAmt, 0) ELSE 0 END
     */
    private NumberExpression<BigDecimal> caseDupBgByCTp(
            StringExpression cTp, NumberExpression<BigDecimal> dupBgAmt, String codeType) {
        return Expressions.numberTemplate(
                BigDecimal.class,
                "CASE WHEN {0} = {1} THEN COALESCE({2}, 0) ELSE 0 END",
                cTp,
                Expressions.constant(codeType),
                dupBgAmt);
    }

    /**
     * BITEMM 품목구분별 단일 필드 합계
     *
     * <p>SUM(CASE WHEN C_TP = 'codeType' THEN field ELSE 0 END)
     */
    private NumberExpression<BigDecimal> sumFieldByCTp(
            StringExpression cTp, String codeType, NumberExpression<BigDecimal> field) {
        return Expressions.numberTemplate(
                BigDecimal.class,
                "COALESCE(SUM(CASE WHEN {0} = {1} THEN {2} ELSE 0 END), 0)",
                cTp,
                Expressions.constant(codeType),
                field);
    }

    /** null 값을 BigDecimal.ZERO로 변환 */
    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
