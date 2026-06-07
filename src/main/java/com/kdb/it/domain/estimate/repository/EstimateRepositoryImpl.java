package com.kdb.it.domain.estimate.repository;

import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.entity.QBestim;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 소요예산 산정 마스터 QueryDSL 목록 검색 구현체.
 *
 * <p>Bestim(e) LEFT JOIN Bprojm(p) — 사업명(abusNm)과 주관부서(svnDpmC)를 사업 마스터에서 가져옵니다.
 * JOIN 조건: p.abusMngNo = e.cncdRfrNo AND p.lstYn = 'Y' (최신 버전 한 건만).</p>
 */
@RequiredArgsConstructor
public class EstimateRepositoryImpl implements EstimateRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 소요예산 산정 목록 동적 검색.
     *
     * <p>bbrC 필터는 Bprojm.svnDpmC(주관부서코드)와 비교합니다.
     * stsTc·cncdRfrNo·bbrC 모두 null/빈값이면 전체 조회(관리자 뷰).</p>
     *
     * @param stsTc     상태구분코드 필터
     * @param cncdRfrNo 관련참조번호(사업관리번호) 필터
     * @param bbrC      주관부서코드 필터 (JWT 클레임 bbrC, Bprojm.svnDpmC와 매핑)
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    @Override
    public List<EstimateDto.ListItem> search(String stsTc, String cncdRfrNo, String bbrC) {
        QBestim e = QBestim.bestim;
        QBprojm p = QBprojm.bprojm;

        BooleanBuilder where = new BooleanBuilder();
        where.and(e.delYn.eq("N"));
        where.and(e.lstYn.eq("Y"));
        if (StringUtils.hasText(stsTc))     { where.and(e.stsTc.eq(stsTc)); }
        if (StringUtils.hasText(cncdRfrNo)) { where.and(e.cncdRfrNo.eq(cncdRfrNo)); }
        // bbrC는 Bprojm.svnDpmC(주관부서코드)로 필터링 — JWT 클레임 bbrC와 동일 도메인
        if (StringUtils.hasText(bbrC))      { where.and(p.svnDpmC.eq(bbrC)); }

        return queryFactory
                .select(Projections.constructor(EstimateDto.ListItem.class,
                        e.rqmBgReqDocNo,   // 소요예산요청문서번호
                        e.docVrsSno,       // 문서버전일련번호
                        e.bgPrnTc,         // 예산성격구분코드
                        e.cncdRfrNo,       // 관련참조번호(사업관리번호)
                        p.abusNm,          // 사업명 (Bprojm에서 조인)
                        e.stsTc,           // 상태구분코드
                        e.fstEnrUsid,      // 최초등록자(요청자) — BaseEntity 상속 필드
                        e.fstEnrDtm        // 최초등록일시(요청일시) — BaseEntity 상속 필드
                ))
                .from(e)
                // 사업관리번호로 조인, 최신버전(lstYn='Y') 1건만 매핑
                .leftJoin(p).on(p.abusMngNo.eq(e.cncdRfrNo).and(p.lstYn.eq("Y")))
                .where(where)
                .orderBy(e.fstEnrDtm.desc())
                .fetch();
    }
}
