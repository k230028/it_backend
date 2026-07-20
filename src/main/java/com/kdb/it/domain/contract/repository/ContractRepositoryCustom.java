package com.kdb.it.domain.contract.repository;

import com.kdb.it.domain.contract.dto.ContractDto;
import java.util.List;
import java.util.Optional;

/** 입찰계약 QueryDSL 동적 검색 인터페이스. */
public interface ContractRepositoryCustom {

    /**
     * 현재 유효 마스터 + 대상명 단일 조회.
     *
     * <p>마스터(lstYn='Y', delYn='N')와 대상명을 1개 쿼리로 가져옵니다. 대상명은 대상구분(ioeC)에 따라
     * Bprojm(사업=ABUS_NM) 또는 Bcostm(전산업무비=CTT_NM)을 cncdRfrNo 키로 LEFT JOIN하여 CASE로 분기합니다.
     * 마스터·대상 분리 조회를 단일 쿼리로 통합해 단건 조회의 DB 왕복을 2회에서 1회로 줄입니다.</p>
     *
     * @param docNo 문서관리번호
     * @return 마스터 + 대상명 행 (문서 없으면 empty)
     */
    Optional<ContractTargetRow> findCurrentWithTargetName(String docNo);

    /**
     * 입찰계약 목록 동적 검색.
     *
     * <p>모든 파라미터가 null/빈값이면 전체 조회(관리자 뷰).</p>
     *
     * @param stsTc     상태구분코드 필터 (nullable)
     * @param ioeC   예산성격구분코드(대상구분) 필터 (nullable)
     * @param cncdRfrNo 관련참조번호(대상관리번호) 필터 (nullable)
     * @param bbrC      주관부서코드 필터. null 또는 빈 문자열이면 전체 부서를 조회
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    List<ContractDto.ListItem> search(String stsTc, String ioeC, String cncdRfrNo, String bbrC);
}
