package com.kdb.it.domain.payment.repository;

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
 * <p>bbrC 필터는 대금지급 대상이 사업(Bprojm)·전산업무비(Bcostm) 2종이라
 * 단일 JOIN이 곤란하여 MVP에서 미적용합니다. 후속 고도화 과제로 등록됨.</p>
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
     * @param bbrC      주관부서코드 필터 (현재 미사용 — 대상 2종 단일 JOIN 곤란, 후속 고도화)
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    @Override
    public List<PaymentDto.ListItem> search(String stsTc, String bgPrnTc, String cncdRfrNo, String bbrC) {
        QBpaymm p = QBpaymm.bpaymm;
        BooleanBuilder where = new BooleanBuilder();
        where.and(p.delYn.eq("N"));
        where.and(p.lstYn.eq("Y"));
        if (StringUtils.hasText(stsTc))     { where.and(p.stsTc.eq(stsTc)); }
        if (StringUtils.hasText(bgPrnTc))   { where.and(p.bgPrnTc.eq(bgPrnTc)); }
        if (StringUtils.hasText(cncdRfrNo)) { where.and(p.cncdRfrNo.eq(cncdRfrNo)); }
        // bbrC: 대상 2종이라 단일 join 곤란 → MVP 미적용(후속 고도화).

        return queryFactory
                .select(Projections.constructor(PaymentDto.ListItem.class,
                        p.docMngNo,      // 문서관리번호
                        p.docVrsSno,     // 문서버전일련번호
                        p.bgPrnTc,       // 예산성격구분코드(대상구분)
                        p.cncdRfrNo,     // 관련참조번호(대상관리번호)
                        p.stsTc,         // 상태구분코드
                        p.cttNm,         // 계약명
                        p.cttAmt,        // 계약금액
                        p.fstEnrUsid,    // 최초등록자(요청자) — BaseEntity 상속 필드
                        p.fstEnrDtm      // 최초등록일시(요청일시) — BaseEntity 상속 필드
                ))
                .from(p)
                .where(where)
                .orderBy(p.fstEnrDtm.desc())
                .fetch();
    }
}
