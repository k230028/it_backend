package com.kdb.it.domain.bizplan.repository;

import com.kdb.it.common.iam.entity.QCorgnI;
import com.kdb.it.domain.bizplan.dto.BizplanDto;
import com.kdb.it.domain.bizplan.entity.QBbizpm;
import com.kdb.it.domain.budget.plan.entity.QBplana;
import com.kdb.it.domain.budget.plan.entity.QBplanm;
import com.kdb.it.domain.budget.project.entity.QBproja;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 사업계획 목록 QueryDSL 구현.
 *
 * <p>BPLANA(계획-사업 관계)에 포함된 사업을 기준으로, BBIZPM(사업계획)과 BPROJA(상태, {@code
 * CNCD_RFR_NO='BIZ-'+ABUS_MNG_NO})를 LEFT JOIN 한다. 한 사업이 여러 연도 계획에 포함될 수 있어 {@code distinct()} 가드를
 * 둔다.
 */
@RequiredArgsConstructor
public class BizplanRepositoryImpl implements BizplanRepositoryCustom {

    /** BPROJA 사업계획 단계 키 접두사 (BizplanService와 동일 값 유지) */
    private static final String BPROJA_KEY_PREFIX = "BIZ-";

    private final JPAQueryFactory queryFactory;

    @Override
    public List<BizplanDto.ListItem> search(String bbrC) {
        QBplana pa = QBplana.bplana;
        QBplanm plan = QBplanm.bplanm;
        QBprojm p = QBprojm.bprojm;
        QBbizpm bp = QBbizpm.bbizpm;
        QBproja a = QBproja.bproja;
        QCorgnI org = QCorgnI.corgnI;

        BooleanBuilder where = new BooleanBuilder();
        where.and(pa.delYn.eq("N"));
        // bbrC는 Bprojm.svnDpmC(주관부서코드)로 필터링 — JWT 클레임 bbrC와 동일 도메인
        if (StringUtils.hasText(bbrC)) {
            where.and(p.svnDpmC.eq(bbrC));
        }

        return queryFactory
                .select(
                        Projections.constructor(
                                BizplanDto.ListItem.class,
                                p.abusMngNo, // 사업관리번호
                                p.abusNm, // 사업명
                                p.svnDpmC, // 주관부서코드
                                // 주관부서명: 조직 마스터(CORGNI) 실시간 부서명 우선, 없으면 저장 스냅샷 사용
                                org.bbrNm.coalesce(p.svnDpmNm),
                                p.bseYy, // 예산연도
                                bp.totRqmAmt, // 총소요금액(사업계획 미생성 시 null)
                                a.stsTc, // 사업계획 상태(21/29, 미생성 시 null=미작성)
                                bp.lstChgDtm)) // 사업계획 최종변경일시
                .distinct()
                .from(pa)
                .join(plan)
                .on(
                        plan.reqDocNo
                                .eq(pa.reqDocNo)
                                .and(plan.delYn.eq("N")))
                .join(p)
                .on(p.abusMngNo.eq(pa.prjMngNo).and(p.lstYn.eq("Y")).and(p.delYn.eq("N")))
                .leftJoin(bp)
                .on(bp.abusMngNo.eq(p.abusMngNo).and(bp.delYn.eq("N")))
                .leftJoin(a)
                .on(
                        a.abusMngNo
                                .eq(p.abusMngNo)
                                .and(a.cncdRfrNo.eq(p.abusMngNo.prepend(BPROJA_KEY_PREFIX)))
                                .and(a.delYn.eq("N")))
                // 주관부서코드 → 조직 마스터 부서명 조인 (delYn='N'인 조직만)
                .leftJoin(org)
                .on(org.prlmOgzCCone.eq(p.svnDpmC).and(org.delYn.eq("N")))
                .where(where)
                .orderBy(p.abusMngNo.desc())
                .fetch();
    }
}
