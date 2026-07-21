package com.kdb.it.domain.estimate.repository;

import com.kdb.it.common.code.entity.QCcodem;
import com.kdb.it.domain.budget.project.entity.QBitemm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.entity.QBestim;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 소요예산 산정 마스터 QueryDSL 목록 검색 구현체.
 *
 * <p>Bprojm(p) LEFT JOIN Bestim(e) — 개발비 품목이 있는 소속 부서 사업을 기준으로 목록을 구성하고, 진행 중인 산정 문서가 있으면 문서 정보를 함께
 * 가져옵니다.
 */
@RequiredArgsConstructor
public class EstimateRepositoryImpl implements EstimateRepositoryCustom {

    /** 소요예산 산정 목록 노출에서 제외할 개발비 감리/컨설팅 비목 코드 */
    private static final String DEV_CONSULTING_IOE_C = "104";

    private final JPAQueryFactory queryFactory;

    /**
     * 소요예산 산정 목록 동적 검색.
     *
     * <p>bbrC 필터는 Bprojm.svnDpmC(주관부서코드)와 비교합니다. stsTc·cncdRfrNo·bbrC 모두 null/빈값이면 전체 조회(관리자 뷰).
     *
     * @param stsTc 상태구분코드 필터
     * @param cncdRfrNo 관련참조번호(사업관리번호) 필터
     * @param bbrC 주관부서코드 필터 (JWT 클레임 bbrC, Bprojm.svnDpmC와 매핑)
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    @Override
    public List<EstimateDto.ListItem> search(String stsTc, String cncdRfrNo, String bbrC) {
        QBestim e = QBestim.bestim;
        QBprojm p = QBprojm.bprojm;
        QBitemm item = QBitemm.bitemm;
        QBitemm devItem = new QBitemm("devItem");
        QCcodem code = QCcodem.ccodem;

        BooleanBuilder where = new BooleanBuilder();
        where.and(p.delYn.eq("N"));
        where.and(p.lstYn.eq("Y"));
        where.and(
                JPAExpressions.selectOne()
                        .from(devItem)
                        .where(
                                devItem.abusMngNo
                                        .eq(p.abusMngNo)
                                        .and(devItem.fntTbCrySno.eq(p.sno))
                                        .and(devItem.lstYn.eq("Y"))
                                        .and(devItem.delYn.eq("N"))
                                        .and(
                                                devItem.ioeC.in(
                                                        JPAExpressions.select(code.cdva)
                                                                .from(code)
                                                                .where(
                                                                        code.cId
                                                                                .eq("IOE_C")
                                                                                .and(
                                                                                        code.cTp.eq(
                                                                                                "IOE_DVC"))
                                                                                .and(
                                                                                        code.cdva
                                                                                                .ne(
                                                                                                        DEV_CONSULTING_IOE_C))
                                                                                .and(
                                                                                        code.delYn
                                                                                                .eq(
                                                                                                        "N"))))))
                        .exists());
        if (StringUtils.hasText(stsTc)) {
            where.and(e.stsTc.eq(stsTc));
        }
        if (StringUtils.hasText(cncdRfrNo)) {
            where.and(p.abusMngNo.eq(cncdRfrNo));
        }
        // bbrC는 Bprojm.svnDpmC(주관부서코드)로 필터링 — JWT 클레임 bbrC와 동일 도메인
        if (StringUtils.hasText(bbrC)) {
            where.and(p.svnDpmC.eq(bbrC));
        }

        NumberExpression<BigDecimal> totalBudget = item.amt.coalesce(BigDecimal.ZERO).sum();

        return queryFactory
                .select(
                        Projections.constructor(
                                EstimateDto.ListItem.class,
                                e.rqmBgReqDocNo, // 소요예산요청문서번호
                                e.docVrsSno, // 문서버전일련번호
                                Expressions.constant("100"), // 소요예산 산정 대상은 정보화사업
                                p.abusMngNo, // 관련참조번호(사업관리번호)
                                p.abusNm, // 사업명 (Bprojm에서 조인)
                                totalBudget.coalesce(BigDecimal.ZERO), // 총사업비
                                p.sttDtm, // 사업 시작일
                                p.endDtm, // 사업 종료일
                                p.svnDpmC, // 담당부서코드
                                p.svnDpmNm, // 담당부서명
                                e.stsTc, // 상태구분코드
                                e.fstEnrUsid, // 최초등록자(요청자) — BaseEntity 상속 필드
                                e.fstEnrDtm // 최초등록일시(요청일시) — BaseEntity 상속 필드
                                ))
                .from(p)
                .leftJoin(e)
                .on(e.cncdRfrNo.eq(p.abusMngNo).and(e.lstYn.eq("Y")).and(e.delYn.eq("N")))
                .leftJoin(item)
                .on(
                        item.abusMngNo
                                .eq(p.abusMngNo)
                                .and(item.fntTbCrySno.eq(p.sno))
                                .and(item.lstYn.eq("Y"))
                                .and(item.delYn.eq("N")))
                .where(where)
                .groupBy(
                        e.rqmBgReqDocNo,
                        e.docVrsSno,
                        p.abusMngNo,
                        p.abusNm,
                        p.sttDtm,
                        p.endDtm,
                        p.svnDpmC,
                        p.svnDpmNm,
                        e.stsTc,
                        e.fstEnrUsid,
                        e.fstEnrDtm)
                .orderBy(e.fstEnrDtm.desc().nullsLast(), p.abusMngNo.asc())
                .fetch();
    }
}
