package com.kdb.it.domain.payment.repository;

import com.kdb.it.domain.payment.dto.PaymentDto;
import java.util.List;
import java.util.Optional;

/**
 * 대금지급 마스터 동적 목록 조회 인터페이스.
 *
 * <p>QueryDSL 기반 구현체 {@link PaymentRepositoryImpl}에서 검색 조건을 조립합니다.</p>
 */
public interface PaymentRepositoryCustom {

    /**
     * 현재 유효 마스터 + 대상명 단일 조회.
     *
     * <p>마스터(lstYn='Y', delYn='N')와 대상명을 1개 쿼리로 가져옵니다. 대상명은 대상구분(bgPrnTc)에 따라
     * Bprojm(사업=ABUS_NM) 또는 Bcostm(전산업무비=CTT_NM)을 cncdRfrNo 키로 LEFT JOIN하여 CASE로 분기합니다.
     * 회차별 지급 명세(1:N)는 본 쿼리에 포함하지 않고 서비스에서 별도 조회합니다.</p>
     *
     * @param docNo 문서관리번호
     * @return 마스터 + 대상명 행 (문서 없으면 empty)
     */
    Optional<PaymentTargetRow> findCurrentWithTargetName(String docNo);

    /**
     * 대금지급 목록 동적 검색.
     *
     * <p>stsTc·bgPrnTc·cncdRfrNo 모두 null/빈값이면 전체 조회.
     * bbrC 필터는 대상 2종(사업/전산업무비) 단일 조인이 곤란하여 MVP 미적용 — 후속 고도화 예정.</p>
     *
     * @param stsTc     상태구분코드 필터
     * @param bgPrnTc   예산성격구분코드(대상구분) 필터
     * @param cncdRfrNo 관련참조번호(대상관리번호) 필터
     * @param bbrC      주관부서코드 필터 (현재 미사용, 후속 고도화)
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    List<PaymentDto.ListItem> search(String stsTc, String bgPrnTc, String cncdRfrNo, String bbrC);
}
