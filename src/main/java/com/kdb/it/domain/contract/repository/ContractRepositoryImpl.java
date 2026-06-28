package com.kdb.it.domain.contract.repository;

import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
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
 * <p>bbrC 필터: 대상구분(bgPrnTc)에 따라 조건부 LEFT JOIN으로 부서 필터를 적용합니다.
 * 사업(100)은 Bprojm.svnDpmC, 전산업무비(200)은 Bcostm.costSvnDpmC와 비교합니다.</p>
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
     * @param bbrC      주관부서코드 필터 — 사업(100)=Bprojm.svnDpmC, 전산업무비(200)=Bcostm.costSvnDpmC
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    @Override
    public List<ContractDto.ListItem> search(String stsTc, String bgPrnTc, String cncdRfrNo, String bbrC) {
        QBcontm ct = QBcontm.bcontm;  // 계약 마스터
        QBprojm p = QBprojm.bprojm;   // 대상구분 100(정보화사업) 주관부서 소스
        QBcostm c = QBcostm.bcostm;   // 대상구분 200(전산업무비) 주관부서 소스

        BooleanBuilder where = new BooleanBuilder();
        where.and(ct.delYn.eq("N"));
        where.and(ct.lstYn.eq("Y"));
        if (StringUtils.hasText(stsTc))     where.and(ct.stsTc.eq(stsTc));
        if (StringUtils.hasText(bgPrnTc))   where.and(ct.bgPrnTc.eq(bgPrnTc));
        if (StringUtils.hasText(cncdRfrNo)) where.and(ct.cncdRfrNo.eq(cncdRfrNo));
        // bbrC 부서 필터: 사업(100)=Bprojm.svnDpmC, 전산업무비(200)=Bcostm.costSvnDpmC와 비교
        if (StringUtils.hasText(bbrC)) {
            where.and(
                    ct.bgPrnTc.eq("100").and(p.svnDpmC.eq(bbrC))
                            .or(ct.bgPrnTc.eq("200").and(c.costSvnDpmC.eq(bbrC)))
            );
        }

        return queryFactory.select(Projections.constructor(ContractDto.ListItem.class,
                        ct.docMngNo, ct.docVrsSno, ct.bgPrnTc, ct.cncdRfrNo, ct.stsTc, ct.cttNm, ct.cttAmt, ct.fstEnrUsid, ct.fstEnrDtm))
                .from(ct)
                .leftJoin(p).on(p.abusMngNo.eq(ct.cncdRfrNo).and(p.lstYn.eq("Y")).and(p.delYn.eq("N")))
                .leftJoin(c).on(c.costBgNo.eq(ct.cncdRfrNo).and(c.lstYn.eq("Y")).and(c.delYn.eq("N")))
                .where(where)
                .orderBy(ct.fstEnrDtm.desc())
                .fetch();
    }
}
