package com.kdb.it.domain.estimate.repository;

import com.kdb.it.domain.estimate.dto.EstimateDto;
import java.util.List;

/** 소요예산 산정 마스터 QueryDSL 동적 조회 인터페이스. */
public interface EstimateRepositoryCustom {

    /**
     * 소요예산 산정 목록 동적 검색.
     *
     * @param stsTc     상태구분코드 필터 (null/빈값이면 전체)
     * @param cncdRfrNo 관련참조번호(사업관리번호) 필터 (null/빈값이면 전체)
     * @param bbrC      주관부서코드 필터 — Bprojm.svnDpmC 기준 (null/빈값이면 전체)
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    List<EstimateDto.ListItem> search(String stsTc, String cncdRfrNo, String bbrC);
}
