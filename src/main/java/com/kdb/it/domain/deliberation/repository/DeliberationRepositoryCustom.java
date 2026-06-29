package com.kdb.it.domain.deliberation.repository;

import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import java.util.List;
import java.util.Optional;

/** 과업심의 QueryDSL 동적 검색 인터페이스. */
public interface DeliberationRepositoryCustom {

    /**
     * 현재 유효 마스터 + 대상명 단일 조회.
     *
     * <p>마스터(lstYn='Y', delYn='N')와 대상명을 1개 쿼리로 가져옵니다. 대상명은 대상구분(bgPrnTc)에 따라
     * Bprojm(사업=ABUS_NM) 또는 Bcostm(전산업무비=CTT_NM)을 cncdRfrNo 키로 LEFT JOIN하여 CASE로 분기합니다.
     * 마스터·대상 분리 조회(2쿼리)를 단일 쿼리로 통합해 단건 조회의 N+1을 제거합니다.</p>
     *
     * @param docNo 문서관리번호
     * @return 마스터 + 대상명 행 (문서 없으면 empty)
     */
    Optional<DeliberationTargetRow> findCurrentWithTargetName(String docNo);

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
