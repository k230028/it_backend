package com.kdb.it.domain.deliberation.repository;

import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import java.util.List;

/** 과업심의 QueryDSL 동적 검색 인터페이스. */
public interface DeliberationRepositoryCustom {

    /**
     * 과업심의 목록 동적 검색.
     *
     * <p>stsTc·bgPrnTc·cncdRfrNo 모두 null/빈값이면 전체 조회(관리자 뷰).
     * bbrC는 대상 2종(정보화사업/전산업무비)에 대해 단일 JOIN이 곤란하므로 MVP 미적용.</p>
     *
     * @param stsTc     상태구분코드 필터
     * @param bgPrnTc   예산성격구분코드(대상구분) 필터
     * @param cncdRfrNo 관련참조번호(대상관리번호) 필터
     * @param bbrC      주관부서코드 필터 (MVP 미적용 — 향후 고도화)
     * @return 조회된 목록 항목 리스트 (최초등록일시 DESC)
     */
    List<DeliberationDto.ListItem> search(String stsTc, String bgPrnTc, String cncdRfrNo, String bbrC);
}
