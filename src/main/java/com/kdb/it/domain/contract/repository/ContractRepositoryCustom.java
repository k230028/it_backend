package com.kdb.it.domain.contract.repository;

import com.kdb.it.domain.contract.dto.ContractDto;
import java.util.List;

/** 입찰계약 QueryDSL 동적 검색 인터페이스. */
public interface ContractRepositoryCustom {

    /**
     * 입찰계약 목록 동적 검색.
     *
     * <p>모든 파라미터가 null/빈값이면 전체 조회(관리자 뷰).</p>
     *
     * @param stsTc     상태구분코드 필터 (nullable)
     * @param bgPrnTc   예산성격구분코드(대상구분) 필터 (nullable)
     * @param cncdRfrNo 관련참조번호(대상관리번호) 필터 (nullable)
     * @param bbrC      주관부서코드 필터 (MVP 미적용, nullable)
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    List<ContractDto.ListItem> search(String stsTc, String bgPrnTc, String cncdRfrNo, String bbrC);
}
