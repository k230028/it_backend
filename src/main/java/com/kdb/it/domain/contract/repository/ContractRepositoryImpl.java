package com.kdb.it.domain.contract.repository;

import com.kdb.it.domain.contract.dto.ContractDto;
import com.kdb.it.domain.contract.entity.QBcontm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 입찰계약 QueryDSL 목록 검색 구현체.
 *
 * <p>대상명(tgtNm)은 대상구분(bgPrnTc)에 따라 Bprojm 또는 Bcostm 두 곳에서 가져와야 하므로
 * JOIN 구조가 복잡합니다. MVP에서는 목록에 대상명을 포함하지 않고,
 * 단건 조회(get) 시 서비스 계층에서 해석합니다.</p>
 *
 * <p>bbrC 필터: 대상 2종(정보화사업/전산업무비)에 대한 단일 JOIN이 곤란하므로
 * MVP에서는 미적용합니다 — 향후 고도화.</p>
 */
@RequiredArgsConstructor
public class ContractRepositoryImpl implements ContractRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 입찰계약 목록 동적 검색.
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
    public List<ContractDto.ListItem> search(String stsTc, String bgPrnTc, String cncdRfrNo, String bbrC) {
        QBcontm c = QBcontm.bcontm;
        BooleanBuilder where = new BooleanBuilder();
        where.and(c.delYn.eq("N"));
        where.and(c.lstYn.eq("Y"));
        if (StringUtils.hasText(stsTc))     where.and(c.stsTc.eq(stsTc));
        if (StringUtils.hasText(bgPrnTc))   where.and(c.bgPrnTc.eq(bgPrnTc));
        if (StringUtils.hasText(cncdRfrNo)) where.and(c.cncdRfrNo.eq(cncdRfrNo));
        // bbrC: 대상 2종이라 단일 join 곤란 → MVP 미적용(후속 고도화).
        return queryFactory.select(Projections.constructor(ContractDto.ListItem.class,
                        c.docMngNo, c.docVrsSno, c.bgPrnTc, c.cncdRfrNo, c.stsTc, c.cttNm, c.cttAmt, c.fstEnrUsid, c.fstEnrDtm))
                .from(c).where(where).orderBy(c.fstEnrDtm.desc()).fetch();
    }
}
