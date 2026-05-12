package com.kdb.it.domain.budget.status.repository;

import com.kdb.it.common.iam.entity.QCorgnI;
import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.project.entity.QBitemm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.kdb.it.domain.budget.status.dto.BudgetStatusDto;
import com.kdb.it.domain.budget.work.entity.QBbugtm;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 예산 현황 QueryDSL 쿼리 구현체
 *
 * <p>
 * 3개 탭(정보화사업/전산업무비/경상사업)별 QueryDSL 피벗 쿼리를 구현합니다.
 * CASE WHEN + SUM + GROUP BY 패턴으로 DB 레벨에서 피벗 처리하여
 * 단일 쿼리로 정제된 데이터를 반환합니다.
 * </p>
 *
 * // Design Ref: §3.5 — QueryDSL 쿼리 전략
 */
@Repository
@RequiredArgsConstructor
public class BudgetStatusQueryRepositoryImpl implements BudgetStatusQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 편성비목 접두어 상수 */
    private static final String IOE_DEV = "IOE-237";       // 개발비
    private static final String IOE_MACH = "IOE-238";      // 기계장치
    private static final String IOE_INTAN = "IOE-239";     // 기타무형자산
    private static final String IOE_RENT = "IOE-231";      // 전산임차료
    private static final String IOE_TRAVEL = "IOE-232";    // 전산여비
    private static final String IOE_SERVICE = "IOE-233";   // 전산용역비
    private static final String IOE_MISC = "IOE-234";      // 전산제비

    /**
     * 정보화사업 예산 현황 조회
     *
     * <p>
     * BPROJM(경상사업 제외) LEFT JOIN BITEMM(품목구분별 피벗) LEFT JOIN BBUGTM(비목별 피벗)
     * GROUP BY 프로젝트 기본정보로 피벗 집계 후 소계/합계를 후계산합니다.
     * </p>
     *
     * @param bgYy 예산년도
     * @return 정보화사업별 편성요청/조정 금액 목록
     */
    @Override
    public List<BudgetStatusDto.ProjectResponse> findProjectStatus(String bgYy) {
        QBprojm p = QBprojm.bprojm;
        QBitemm i = QBitemm.bitemm;
        QBbugtm b = new QBbugtm("b");
        QCorgnI svnOrg = new QCorgnI("svnOrg");  // 주관부서 조직 조인용
        QCorgnI itOrg = new QCorgnI("itOrg");     // IT담당부서 조직 조인용

        // 담당자 행번 → 이름 변환용 스칼라 서브쿼리 (JPQL 엔티티명 사용)
        StringExpression svnDpmTlrNm = Expressions.stringTemplate(
                "(SELECT u.usrNm FROM CuserI u WHERE u.eno = {0})", p.svnDpmTlr);
        StringExpression svnDpmCgprNm = Expressions.stringTemplate(
                "(SELECT u.usrNm FROM CuserI u WHERE u.eno = {0})", p.svnDpmCgpr);
        StringExpression itDpmTlrNm = Expressions.stringTemplate(
                "(SELECT u.usrNm FROM CuserI u WHERE u.eno = {0})", p.itDpmTlr);
        StringExpression itDpmCgprNm = Expressions.stringTemplate(
                "(SELECT u.usrNm FROM CuserI u WHERE u.eno = {0})", p.itDpmCgpr);

        // 편성요청 금액: BITEMM의 GCL_AMT * COALESCE(XCR, 1)를 품목구분별로 피벗
        NumberExpression<BigDecimal> reqDev = sumItemAmtByPrefix(i, IOE_DEV);
        NumberExpression<BigDecimal> reqMach = sumItemAmtByPrefix(i, IOE_MACH);
        NumberExpression<BigDecimal> reqIntan = sumItemAmtByPrefix(i, IOE_INTAN);
        NumberExpression<BigDecimal> reqRent = sumItemAmtByPrefix(i, IOE_RENT);
        NumberExpression<BigDecimal> reqTravel = sumItemAmtByPrefix(i, IOE_TRAVEL);
        NumberExpression<BigDecimal> reqService = sumItemAmtByPrefix(i, IOE_SERVICE);
        NumberExpression<BigDecimal> reqMisc = sumItemAmtByPrefix(i, IOE_MISC);

        // 조정(편성) 금액: BBUGTM의 DUP_BG를 비목코드별로 피벗
        NumberExpression<BigDecimal> adjDev = sumDupBgByPrefix(b, IOE_DEV);
        NumberExpression<BigDecimal> adjMach = sumDupBgByPrefix(b, IOE_MACH);
        NumberExpression<BigDecimal> adjIntan = sumDupBgByPrefix(b, IOE_INTAN);
        NumberExpression<BigDecimal> adjRent = sumDupBgByPrefix(b, IOE_RENT);
        NumberExpression<BigDecimal> adjTravel = sumDupBgByPrefix(b, IOE_TRAVEL);
        NumberExpression<BigDecimal> adjService = sumDupBgByPrefix(b, IOE_SERVICE);
        NumberExpression<BigDecimal> adjMisc = sumDupBgByPrefix(b, IOE_MISC);

        List<Tuple> tuples = queryFactory
                .select(
                        p.prjMngNo, p.prjTp, p.pulDtt, p.prjNm, p.prjDes,
                        p.svnHdq, p.svnDpm, svnOrg.bbrNm, p.svnDpmTlr, svnDpmTlrNm, p.svnDpmCgpr, svnDpmCgprNm,
                        p.itDpm, itOrg.bbrNm, p.itDpmTlr, itDpmTlrNm, p.itDpmCgpr, itDpmCgprNm,
                        p.prjPulPtt, p.sttDt, p.endDt, p.rprSts, p.edrt,
                        reqDev, reqMach, reqIntan, reqRent, reqTravel, reqService, reqMisc,
                        adjDev, adjMach, adjIntan, adjRent, adjTravel, adjService, adjMisc
                )
                .from(p)
                .leftJoin(svnOrg).on(svnOrg.prlmOgzCCone.eq(p.svnDpm))
                .leftJoin(itOrg).on(itOrg.prlmOgzCCone.eq(p.itDpm))
                .leftJoin(i).on(
                        i.prjMngNo.eq(p.prjMngNo),
                        i.prjSno.eq(p.prjSno),
                        i.delYn.eq("N"),
                        i.lstYn.eq("Y")
                )
                .leftJoin(b).on(
                        b.orcTb.eq("BITEMM"),
                        b.orcPkVl.eq(i.gclMngNo),
                        b.bgYy.eq(bgYy),
                        b.delYn.eq("N")
                )
                .where(
                        p.bgYy.eq(bgYy),
                        p.ornYn.ne("Y"),
                        p.delYn.eq("N"),
                        p.lstYn.eq("Y")
                )
                .groupBy(
                        p.prjMngNo, p.prjSno, p.prjTp, p.pulDtt, p.prjNm, p.prjDes,
                        p.svnHdq, p.svnDpm, svnOrg.bbrNm, p.svnDpmTlr, p.svnDpmCgpr,
                        p.itDpm, itOrg.bbrNm, p.itDpmTlr, p.itDpmCgpr,
                        p.prjPulPtt, p.sttDt, p.endDt, p.rprSts, p.edrt
                )
                .orderBy(p.prjMngNo.asc())
                .fetch();

        return tuples.stream().map(t -> {
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
                    t.get(p.prjMngNo), t.get(p.prjTp), t.get(p.pulDtt),
                    t.get(p.prjNm), t.get(p.prjDes),
                    t.get(p.svnHdq), t.get(p.svnDpm), t.get(svnOrg.bbrNm),
                    t.get(p.svnDpmTlr), t.get(svnDpmTlrNm),
                    t.get(p.svnDpmCgpr), t.get(svnDpmCgprNm),
                    t.get(p.itDpm), t.get(itOrg.bbrNm),
                    t.get(p.itDpmTlr), t.get(itDpmTlrNm),
                    t.get(p.itDpmCgpr), t.get(itDpmCgprNm),
                    t.get(p.prjPulPtt), t.get(p.sttDt), t.get(p.endDt),
                    t.get(p.rprSts), t.get(p.edrt),
                    rDev, rMach, rIntan, rAsset,
                    rRent, rTravel, rService, rMisc, rCost, rTotal,
                    aDev, aMach, aIntan, aAsset,
                    aRent, aTravel, aService, aMisc, aCost, aTotal
            );
        }).toList();
    }

    /**
     * 전산업무비 예산 현황 조회
     *
     * <p>
     * BCOSTM LEFT JOIN BBUGTM 매핑. 전산업무비는 레코드 1건이 1개 비목에 대응하므로
     * 피벗 불필요. 비목코드 접두어로 해당 컬럼에 금액을 배치합니다.
     * </p>
     *
     * @param bgYy 예산년도
     * @return 전산업무비별 편성요청/조정 금액 목록
     */
    @Override
    public List<BudgetStatusDto.CostResponse> findCostStatus(String bgYy) {
        QBcostm c = QBcostm.bcostm;
        QBbugtm b = new QBbugtm("b");
        // 전년도 편성 조회용 별칭 (동일 IT_MNGC_NO로 전년도 BBUGTM JOIN)
        QBbugtm bPrev = new QBbugtm("bPrev");
        // 전년도 편성 최대 순번 서브쿼리용 별칭
        QBbugtm bMaxPrev = new QBbugtm("bMaxPrev");
        QCorgnI dpmOrg = new QCorgnI("dpmOrg");   // 담당부서 조직 조인용
        QCorgnI temOrg = new QCorgnI("temOrg");    // 담당팀 조직 조인용

        String prevYy = String.valueOf(Integer.parseInt(bgYy) - 1);

        // 전년도 편성금액: 전년도 BBUGTM DUP_BG를 IOE_C 접두어별 분배 (편성 없으면 0)
        NumberExpression<BigDecimal> reqRent = caseDupBgByPrefix(c.ioeC, bPrev.dupBg, IOE_RENT);
        NumberExpression<BigDecimal> reqTravel = caseDupBgByPrefix(c.ioeC, bPrev.dupBg, IOE_TRAVEL);
        NumberExpression<BigDecimal> reqService = caseDupBgByPrefix(c.ioeC, bPrev.dupBg, IOE_SERVICE);
        NumberExpression<BigDecimal> reqMisc = caseDupBgByPrefix(c.ioeC, bPrev.dupBg, IOE_MISC);
        NumberExpression<BigDecimal> reqTotal = Expressions.numberTemplate(BigDecimal.class,
                "COALESCE({0}, 0)", bPrev.dupBg);

        // 금년도 조정: 금년도 BBUGTM의 DUP_BG를 IOE_C 접두어별 분배
        NumberExpression<BigDecimal> adjRent = caseDupBgByPrefix(c.ioeC, b.dupBg, IOE_RENT);
        NumberExpression<BigDecimal> adjTravel = caseDupBgByPrefix(c.ioeC, b.dupBg, IOE_TRAVEL);
        NumberExpression<BigDecimal> adjService = caseDupBgByPrefix(c.ioeC, b.dupBg, IOE_SERVICE);
        NumberExpression<BigDecimal> adjMisc = caseDupBgByPrefix(c.ioeC, b.dupBg, IOE_MISC);
        NumberExpression<BigDecimal> adjTotal = Expressions.numberTemplate(BigDecimal.class,
                "COALESCE({0}, 0)", b.dupBg);

        List<Tuple> tuples = queryFactory
                .select(
                        c.itMngcNo, c.pulDtt, c.abusC, c.ioeC,
                        c.biceDpm, dpmOrg.bbrNm, c.biceTem, temOrg.bbrNm,
                        c.cttNm, c.cttOpp, c.infPrtYn, c.itMngcTp,
                        reqRent, reqTravel, reqService, reqMisc, reqTotal,
                        adjRent, adjTravel, adjService, adjMisc, adjTotal
                )
                .from(c)
                .leftJoin(dpmOrg).on(dpmOrg.prlmOgzCCone.eq(c.biceDpm))
                .leftJoin(temOrg).on(temOrg.prlmOgzCCone.eq(c.biceTem))
                .leftJoin(b).on(
                        b.orcTb.eq("BCOSTM"),
                        b.orcPkVl.eq(c.itMngcNo),
                        b.bgYy.eq(bgYy),
                        b.delYn.eq("N")
                )
                .leftJoin(bPrev).on(
                        bPrev.orcTb.eq("BCOSTM"),
                        // 계속항목은 cncdItMngcNo 기준, 신규항목은 itMngcNo 기준으로 전년도 편성 조회
                        Expressions.booleanTemplate(
                                "COALESCE({0}, {1}) = {2}",
                                c.cncdItMngcNo, c.itMngcNo, bPrev.orcPkVl),
                        bPrev.bgYy.eq(prevYy),
                        bPrev.delYn.eq("N"),
                        // 동일 관리번호에 여러 편성건이 있을 경우 마지막 편성건(ORC_SNO_VL 최대값)만 선택
                        bPrev.orcSnoVl.eq(
                                JPAExpressions.select(bMaxPrev.orcSnoVl.max())
                                        .from(bMaxPrev)
                                        .where(
                                                bMaxPrev.orcTb.eq("BCOSTM"),
                                                Expressions.booleanTemplate("COALESCE({0}, {1}) = {2}",
                                                        c.cncdItMngcNo, c.itMngcNo, bMaxPrev.orcPkVl),
                                                bMaxPrev.bgYy.eq(prevYy),
                                                bMaxPrev.delYn.eq("N")
                                        ))
                )
                .where(
                        c.bgYy.eq(bgYy),
                        c.delYn.eq("N"),
                        c.lstYn.eq("Y")
                )
                .orderBy(c.itMngcNo.asc())
                .fetch();

        return tuples.stream().map(t -> new BudgetStatusDto.CostResponse(
                t.get(c.itMngcNo), t.get(c.pulDtt), t.get(c.abusC), t.get(c.ioeC),
                t.get(c.biceDpm), t.get(dpmOrg.bbrNm), t.get(c.biceTem), t.get(temOrg.bbrNm),
                t.get(c.cttNm), t.get(c.cttOpp), t.get(c.infPrtYn), t.get(c.itMngcTp),
                nvl(t.get(reqRent)), nvl(t.get(reqTravel)),
                nvl(t.get(reqService)), nvl(t.get(reqMisc)), nvl(t.get(reqTotal)),
                nvl(t.get(adjRent)), nvl(t.get(adjTravel)),
                nvl(t.get(adjService)), nvl(t.get(adjMisc)), nvl(t.get(adjTotal))
        )).toList();
    }

    /**
     * 경상사업 예산 현황 조회
     *
     * <p>
     * BPROJM(ORN_YN='Y') LEFT JOIN BITEMM으로 기계장치(IOE-238)과
     * 기타무형자산(IOE-239)을 분리하여 조회합니다.
     * 단가(unitPrice = amt / qtt)는 후계산합니다.
     * </p>
     *
     * @param bgYy 예산년도
     * @return 경상사업별 기계장치/기타무형자산 상세 목록
     */
    @Override
    public List<BudgetStatusDto.OrdinaryResponse> findOrdinaryStatus(String bgYy) {
        QBprojm p = QBprojm.bprojm;
        QBitemm i = QBitemm.bitemm;

        // 기계장치 (IOE-238)
        StringExpression machCur = Expressions.stringTemplate(
                "MAX(CASE WHEN {0} LIKE {1} THEN {2} END)",
                i.ioeC, Expressions.constant(IOE_MACH + "%"), i.cur);
        NumberExpression<BigDecimal> machQtt = sumFieldByPrefix(i, IOE_MACH, i.gclQtt);
        NumberExpression<BigDecimal> machAmt = sumFieldByPrefix(i, IOE_MACH, i.gclAmt);
        NumberExpression<BigDecimal> machAmtKrw = sumItemAmtByPrefix(i, IOE_MACH);

        // 기타무형자산 (IOE-239)
        StringExpression intanCur = Expressions.stringTemplate(
                "MAX(CASE WHEN {0} LIKE {1} THEN {2} END)",
                i.ioeC, Expressions.constant(IOE_INTAN + "%"), i.cur);
        NumberExpression<BigDecimal> intanQtt = sumFieldByPrefix(i, IOE_INTAN, i.gclQtt);
        NumberExpression<BigDecimal> intanAmt = sumFieldByPrefix(i, IOE_INTAN, i.gclAmt);
        NumberExpression<BigDecimal> intanAmtKrw = sumItemAmtByPrefix(i, IOE_INTAN);

        List<Tuple> tuples = queryFactory
                .select(
                        p.prjMngNo, p.pulDtt, p.prjNm, p.prjDes,
                        machCur, machQtt, machAmt, machAmtKrw,
                        intanCur, intanQtt, intanAmt, intanAmtKrw
                )
                .from(p)
                .leftJoin(i).on(
                        i.prjMngNo.eq(p.prjMngNo),
                        i.prjSno.eq(p.prjSno),
                        i.delYn.eq("N"),
                        i.lstYn.eq("Y")
                )
                .where(
                        p.bgYy.eq(bgYy),
                        p.ornYn.eq("Y"),
                        p.delYn.eq("N"),
                        p.lstYn.eq("Y")
                )
                .groupBy(p.prjMngNo, p.prjSno, p.pulDtt, p.prjNm, p.prjDes)
                .orderBy(p.prjMngNo.asc())
                .fetch();

        return tuples.stream().map(t -> {
            // 단가 후계산: unitPrice = amt / qtt (0으로 나누기 방지)
            BigDecimal mQtt = nvl(t.get(machQtt));
            BigDecimal mAmt = nvl(t.get(machAmt));
            BigDecimal mUnitPrice = mQtt.compareTo(BigDecimal.ZERO) > 0
                    ? mAmt.divide(mQtt, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            BigDecimal iQtt = nvl(t.get(intanQtt));
            BigDecimal iAmt = nvl(t.get(intanAmt));
            BigDecimal iUnitPrice = iQtt.compareTo(BigDecimal.ZERO) > 0
                    ? iAmt.divide(iQtt, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            return new BudgetStatusDto.OrdinaryResponse(
                    t.get(p.prjMngNo), t.get(p.pulDtt), t.get(p.prjNm), t.get(p.prjDes),
                    t.get(machCur), mQtt, mUnitPrice, mAmt, nvl(t.get(machAmtKrw)),
                    t.get(intanCur), iQtt, iUnitPrice, iAmt, nvl(t.get(intanAmtKrw))
            );
        }).toList();
    }

    // ===== 헬퍼 메서드 =====

    /**
     * BITEMM 품목구분별 원화환산 금액 피벗
     *
     * <p>SUM(CASE WHEN ioeC LIKE 'prefix%' THEN gclAmt * COALESCE(xcr, 1) ELSE 0 END)</p>
     */
    private NumberExpression<BigDecimal> sumItemAmtByPrefix(QBitemm i, String prefix) {
        return Expressions.numberTemplate(BigDecimal.class,
                "COALESCE(SUM(CASE WHEN {0} LIKE {1} THEN {2} * COALESCE({3}, 1) ELSE 0 END), 0)",
                i.ioeC, Expressions.constant(prefix + "%"), i.gclAmt, i.xcr);
    }

    /**
     * BBUGTM 비목코드별 편성예산 피벗
     *
     * <p>SUM(CASE WHEN ioeC LIKE 'prefix%' THEN dupBg ELSE 0 END)</p>
     */
    private NumberExpression<BigDecimal> sumDupBgByPrefix(QBbugtm b, String prefix) {
        return Expressions.numberTemplate(BigDecimal.class,
                "COALESCE(SUM(CASE WHEN {0} LIKE {1} THEN {2} ELSE 0 END), 0)",
                b.ioeC, Expressions.constant(prefix + "%"), b.dupBg);
    }

    /**
     * 비목코드 접두어별 편성예산 분배 (비집계, 전산업무비용)
     *
     * <p>CASE WHEN ioeC LIKE 'prefix%' THEN COALESCE(dupBg, 0) ELSE 0 END</p>
     */
    private NumberExpression<BigDecimal> caseDupBgByPrefix(StringExpression ioeC,
                                                           NumberExpression<BigDecimal> dupBg,
                                                           String prefix) {
        return Expressions.numberTemplate(BigDecimal.class,
                "CASE WHEN {0} LIKE {1} THEN COALESCE({2}, 0) ELSE 0 END",
                ioeC, Expressions.constant(prefix + "%"), dupBg);
    }

    /**
     * BITEMM 품목구분별 단일 필드 합계
     *
     * <p>SUM(CASE WHEN ioeC LIKE 'prefix%' THEN field ELSE 0 END)</p>
     */
    private NumberExpression<BigDecimal> sumFieldByPrefix(QBitemm i, String prefix,
                                                          NumberExpression<BigDecimal> field) {
        return Expressions.numberTemplate(BigDecimal.class,
                "COALESCE(SUM(CASE WHEN {0} LIKE {1} THEN {2} ELSE 0 END), 0)",
                i.ioeC, Expressions.constant(prefix + "%"), field);
    }

    /**
     * null 값을 BigDecimal.ZERO로 변환
     */
    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
