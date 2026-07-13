package com.kdb.it.domain.bizplan.repository;

import com.kdb.it.domain.bizplan.dto.BizplanDto;
import java.util.List;

/** 사업계획 목록 동적 조회 (QueryDSL 구현은 {@code BizplanRepositoryImpl}). */
public interface BizplanRepositoryCustom {

    /**
     * 사업계획 목록 검색 — 정보기술부문 계획(BPLANA)에 포함된 사업 기준.
     *
     * @param bbrC 주관부서코드 필터(Bprojm.svnDpmC 비교). null/빈값이면 전체(관리자 뷰).
     * @return 목록 항목 (사업관리번호 내림차순)
     */
    List<BizplanDto.ListItem> search(String bbrC);
}
