package com.kdb.it.domain.deliberation.repository;

import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.deliberation.entity.Bdelim;
import com.kdb.it.domain.deliberation.entity.QBdelim;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 과업심의 QueryDSL 목록 검색 구현체.
 *
 * <p>대상명(tgtNm)은 대상구분(ioeC)에 따라 Bprojm 또는 Bcostm 두 곳에서 가져와야 하므로
 * JOIN 구조가 복잡합니다. MVP에서는 목록에 대상명을 포함하지 않고,
 * 단건 조회(get) 시 서비스 계층에서 해석합니다.</p>
 *
 * <p>bbrC 필터: 대상구분(ioeC)에 따라 조건부 LEFT JOIN으로 부서 필터를 적용합니다.
 * 사업(100)은 Bprojm.svnDpmC, 전산업무비(200)은 Bcostm.costSvnDpmC와 비교합니다.</p>
 */
@RequiredArgsConstructor
public class DeliberationRepositoryImpl implements DeliberationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 과업심의 목록 동적 검색.
     *
     * <p>stsTc·ioeC·cncdRfrNo 모두 null/빈값이면 전체 조회(관리자 뷰).</p>
     *
     * @param stsTc     상태구분코드 필터
     * @param ioeC   예산성격구분코드(대상구분) 필터
     * @param cncdRfrNo 관련참조번호(대상관리번호) 필터
     * @param bbrC      주관부서코드 필터 — 사업(100)=Bprojm.svnDpmC, 전산업무비(200)=Bcostm.costSvnDpmC
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    @Override
    public List<DeliberationDto.ListItem> search(String stsTc, String ioeC, String cncdRfrNo, String bbrC) {
        QBdelim d = QBdelim.bdelim;
        QBprojm p = QBprojm.bprojm;   // 대상구분 100(정보화사업) 주관부서 소스
        QBcostm c = QBcostm.bcostm;   // 대상구분 200(전산업무비) 주관부서 소스

        BooleanBuilder where = new BooleanBuilder();
        where.and(d.delYn.eq("N"));
        where.and(d.lstYn.eq("Y"));
        if (StringUtils.hasText(stsTc))     where.and(d.stsTc.eq(stsTc));
        if (StringUtils.hasText(ioeC))   where.and(d.ioeC.eq(ioeC));
        if (StringUtils.hasText(cncdRfrNo)) where.and(d.cncdRfrNo.eq(cncdRfrNo));
        // bbrC 부서 필터: 대상구분(ioeC)은 100(정보화사업)·200(전산업무비) 2종뿐.
        // 사업=Bprojm.svnDpmC, 전산업무비=Bcostm.costSvnDpmC와 비교. 그룹핑 명시(anyOf/allOf)로 우선순위 모호성 제거.
        if (StringUtils.hasText(bbrC)) {
            where.and(Expressions.anyOf(
                    Expressions.allOf(d.ioeC.eq("100"), p.svnDpmC.eq(bbrC)),
                    Expressions.allOf(d.ioeC.eq("200"), c.costSvnDpmC.eq(bbrC))
            ));
        }

        return queryFactory.select(Projections.constructor(DeliberationDto.ListItem.class,
                        d.docMngNo, d.docVrsSno, d.ioeC, d.cncdRfrNo, d.stsTc, d.taskDbrRltTc, d.fstEnrUsid, d.fstEnrDtm))
                .distinct()
                .from(d)
                .leftJoin(p).on(p.abusMngNo.eq(d.cncdRfrNo).and(p.lstYn.eq("Y")).and(p.delYn.eq("N")))
                .leftJoin(c).on(c.costBgNo.eq(d.cncdRfrNo).and(c.lstYn.eq("Y")).and(c.delYn.eq("N")))
                .where(where)
                .orderBy(d.fstEnrDtm.desc())
                .fetch();
    }

    @Override
    public Optional<DeliberationDetailRow> findCurrentDetail(String docNo) {
        QBdelim d = QBdelim.bdelim;
        QBprojm p = QBprojm.bprojm;
        QBcostm c = QBcostm.bcostm;
        DeliberationDetailRow row = queryFactory
                .select(Projections.constructor(DeliberationDetailRow.class,
                        d.docMngNo, d.docVrsSno, d.ioeC, d.cncdRfrNo,
                        new CaseBuilder()
                                .when(d.ioeC.eq("100")).then(p.abusNm)
                                .when(d.ioeC.eq("200")).then(c.cttNm)
                                .otherwise(Expressions.nullExpression(String.class)),
                        d.stsTc, d.reqCone, d.taskDbrTc, d.taskDbrRltTc, d.taskDbrDt,
                        d.taskDbrTod, d.taskDbrOmtYn, d.taskDbrOmtRsn, d.opnnCone,
                        d.apvTrdnRsnCone, d.fstEnrUsid, d.fstEnrDtm))
                .distinct()
                .from(d)
                .leftJoin(p).on(p.abusMngNo.eq(d.cncdRfrNo).and(p.lstYn.eq("Y")).and(p.delYn.eq("N")))
                .leftJoin(c).on(c.costBgNo.eq(d.cncdRfrNo).and(c.lstYn.eq("Y")).and(c.delYn.eq("N")))
                .where(d.docMngNo.eq(docNo).and(d.lstYn.eq("Y")).and(d.delYn.eq("N")))
                .fetchOne();
        return Optional.ofNullable(row);
    }

    /**
     * 현재 유효 마스터 + 대상명 단일 조회.
     *
     * <p>마스터와 대상명을 한 번의 쿼리(Tuple)로 가져옵니다. 대상명은 대상구분(ioeC)에 따라
     * Bprojm(100=사업, ABUS_NM) 또는 Bcostm(200=전산업무비, CTT_NM)을 cncdRfrNo 키로 LEFT JOIN하여
     * CASE 식으로 분기합니다. 대상 레코드가 없으면 LEFT JOIN 특성상 대상명은 null입니다.</p>
     *
     * @param docNo 문서관리번호
     * @return 마스터 + 대상명 행 (문서 없으면 empty)
     */
    @Override
    public Optional<DeliberationTargetRow> findCurrentWithTargetName(String docNo) {
        QBdelim d = QBdelim.bdelim;
        QBprojm p = QBprojm.bprojm;
        QBcostm c = QBcostm.bcostm;
        Tuple row = queryFactory
                .select(d,
                        new CaseBuilder()
                                .when(d.ioeC.eq("100")).then(p.abusNm)
                                .when(d.ioeC.eq("200")).then(c.cttNm)
                                .otherwise(Expressions.nullExpression(String.class)))
                // BPROJM/BCOSTM 2중 LEFT JOIN이 버전 전환 중 lstYn='Y' 중복 행으로 팬아웃되어
                // NonUniqueResultException이 나는 것을 방어한다(search()와 동일한 가드).
                .distinct()
                .from(d)
                .leftJoin(p).on(p.abusMngNo.eq(d.cncdRfrNo).and(p.lstYn.eq("Y")).and(p.delYn.eq("N")))
                .leftJoin(c).on(c.costBgNo.eq(d.cncdRfrNo).and(c.lstYn.eq("Y")).and(c.delYn.eq("N")))
                .where(d.docMngNo.eq(docNo).and(d.lstYn.eq("Y")).and(d.delYn.eq("N")))
                .fetchOne();
        if (row == null) return Optional.empty();
        return Optional.of(new DeliberationTargetRow(row.get(0, Bdelim.class), row.get(1, String.class)));
    }
}
