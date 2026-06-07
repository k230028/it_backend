package com.kdb.it.domain.payment.repository;

import com.kdb.it.domain.payment.dto.PaymentDto;
import java.util.List;

/**
 * 대금지급 마스터 동적 목록 조회 인터페이스.
 *
 * <p>QueryDSL 기반 구현체 {@link PaymentRepositoryImpl}에서 검색 조건을 조립합니다.</p>
 */
public interface PaymentRepositoryCustom {

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
