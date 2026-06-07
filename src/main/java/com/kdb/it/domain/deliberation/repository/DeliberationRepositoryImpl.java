package com.kdb.it.domain.deliberation.repository;

import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.deliberation.entity.QBdelim;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 과업심의 QueryDSL 목록 검색 구현체.
 *
 * <p>대상명(tgtNm)은 대상구분(bgPrnTc)에 따라 Bprojm 또는 Bcostm 두 곳에서 가져와야 하므로
 * JOIN 구조가 복잡합니다. MVP에서는 목록에 대상명을 포함하지 않고,
 * 단건 조회(get) 시 서비스 계층에서 해석합니다.</p>
 *
 * <p>bbrC 필터: 대상 2종(정보화사업/전산업무비)에 대한 단일 JOIN이 곤란하므로
 * MVP에서는 미적용합니다 — 향후 고도화.</p>
 */
@RequiredArgsConstructor
public class DeliberationRepositoryImpl implements DeliberationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 과업심의 목록 동적 검색.
     *
     * <p>stsTc·bgPrnTc·cncdRfrNo 모두 null/빈값이면 전체 조회(관리자 뷰).</p>
     *
     * @param stsTc     상태구분코드 필터
     * @param bgPrnTc   예산성격구분코드(대상구분) 필터
     * @param cncdRfrNo 관련참조번호(대상관리번호) 필터
     * @param bbrC      주관부서코드 필터 (MVP 미적용)
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    @Override
    public List<DeliberationDto.ListItem> search(String stsTc, String bgPrnTc, String cncdRfrNo, String bbrC) {
        QBdelim d = QBdelim.bdelim;
        BooleanBuilder where = new BooleanBuilder();
        where.and(d.delYn.eq("N"));
        where.and(d.lstYn.eq("Y"));
        if (StringUtils.hasText(stsTc))     where.and(d.stsTc.eq(stsTc));
        if (StringUtils.hasText(bgPrnTc))   where.and(d.bgPrnTc.eq(bgPrnTc));
        if (StringUtils.hasText(cncdRfrNo)) where.and(d.cncdRfrNo.eq(cncdRfrNo));
        // bbrC: 대상 2종(사업/전산업무비)이라 단일 join 곤란 → MVP 미적용(후속 고도화).
        return queryFactory.select(Projections.constructor(DeliberationDto.ListItem.class,
                        d.docMngNo, d.docVrsSno, d.bgPrnTc, d.cncdRfrNo, d.stsTc, d.taskDbrRltTc, d.fstEnrUsid, d.fstEnrDtm))
                .from(d).where(where).orderBy(d.fstEnrDtm.desc()).fetch();
    }
}
