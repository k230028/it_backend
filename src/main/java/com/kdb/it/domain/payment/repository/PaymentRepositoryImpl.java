package com.kdb.it.domain.payment.repository;

import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.kdb.it.domain.payment.dto.PaymentDto;
import com.kdb.it.domain.payment.entity.QBpaymm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 대금지급 마스터 QueryDSL 목록 검색 구현체.
 *
 * <p>bbrC 필터: 대상구분(bgPrnTc)에 따라 조건부 LEFT JOIN으로 부서 필터를 적용합니다.
 * 사업(100)은 Bprojm.svnDpmC, 전산업무비(200)은 Bcostm.costSvnDpmC와 비교합니다.</p>
 */
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 대금지급 목록 동적 검색.
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
    public List<PaymentDto.ListItem> search(String stsTc, String bgPrnTc, String cncdRfrNo, String bbrC) {
        QBpaymm pm = QBpaymm.bpaymm;  // 지급 마스터
        QBprojm p = QBprojm.bprojm;   // 대상구분 100(정보화사업) 주관부서 소스
        QBcostm c = QBcostm.bcostm;   // 대상구분 200(전산업무비) 주관부서 소스

        BooleanBuilder where = new BooleanBuilder();
        where.and(pm.delYn.eq("N"));
        where.and(pm.lstYn.eq("Y"));
        if (StringUtils.hasText(stsTc))     { where.and(pm.stsTc.eq(stsTc)); }
        if (StringUtils.hasText(bgPrnTc))   { where.and(pm.bgPrnTc.eq(bgPrnTc)); }
        if (StringUtils.hasText(cncdRfrNo)) { where.and(pm.cncdRfrNo.eq(cncdRfrNo)); }
        // bbrC 부서 필터: 사업(100)=Bprojm.svnDpmC, 전산업무비(200)=Bcostm.costSvnDpmC와 비교
        if (StringUtils.hasText(bbrC)) {
            where.and(
                    pm.bgPrnTc.eq("100").and(p.svnDpmC.eq(bbrC))
                            .or(pm.bgPrnTc.eq("200").and(c.costSvnDpmC.eq(bbrC)))
            );
        }

        return queryFactory
                .select(Projections.constructor(PaymentDto.ListItem.class,
                        pm.docMngNo,      // 문서관리번호
                        pm.docVrsSno,     // 문서버전일련번호
                        pm.bgPrnTc,       // 예산성격구분코드(대상구분)
                        pm.cncdRfrNo,     // 관련참조번호(대상관리번호)
                        pm.stsTc,         // 상태구분코드
                        pm.cttNm,         // 계약명
                        pm.cttAmt,        // 계약금액
                        pm.fstEnrUsid,    // 최초등록자(요청자) — BaseEntity 상속 필드
                        pm.fstEnrDtm      // 최초등록일시(요청일시) — BaseEntity 상속 필드
                ))
                .from(pm)
                .leftJoin(p).on(p.abusMngNo.eq(pm.cncdRfrNo).and(p.lstYn.eq("Y")).and(p.delYn.eq("N")))
                .leftJoin(c).on(c.costBgNo.eq(pm.cncdRfrNo).and(c.lstYn.eq("Y")).and(c.delYn.eq("N")))
                .where(where)
                .orderBy(pm.fstEnrDtm.desc())
                .fetch();
    }
}
