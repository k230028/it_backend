package com.kdb.it.common.code.repository;

import com.kdb.it.common.code.entity.Ccodem;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 공통코드 QueryDSL 커스텀 리포지토리 인터페이스
 */
public interface CodeRepositoryCustom {

    /**
     * 기준일자와 코드ID를 바탕으로 조회
     *
     * @param cdId       조회할 코드ID
     * @param targetDate 기준일자 (Nullable, null일 경우 시스템 현재 날짜 적용)
     * @return 조회된 공통코드 엔티티
     */
    Optional<Ccodem> findByCIdWithValidDate(String cdId, LocalDate targetDate);

    /**
     * 기준일자와 코드값구분을 바탕으로 다건 조회
     *
     * @param cttTp      조회할 코드값구분
     * @param targetDate 기준일자 (Nullable, null일 경우 시스템 현재 날짜 적용)
     * @return 조회된 공통코드 엔티티 목록
     */
    List<Ccodem> findByCttTpWithValidDate(String cttTp, LocalDate targetDate);

    /**
     * 논리 삭제되지 않은 전체 공통코드를 코드순서 오름차순(null 마지막)으로 조회
     *
     * @return 활성 공통코드 엔티티 목록 (코드순서 오름차순)
     */
    List<Ccodem> findAllActive();
}
