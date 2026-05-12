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
     * 코드ID + 코드값 + 기준일자로 단건 조회
     *
     * @param cId        코드ID (예: CUR, PRJ_TP)
     * @param cdva       코드값 (예: 001, STA)
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜)
     */
    Optional<Ccodem> findByCIdAndCdvaWithValidDate(String cId, String cdva, LocalDate targetDate);

    /**
     * 코드ID 기준 다건 조회 (카테고리 전체)
     *
     * @param cId        코드ID
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜)
     */
    List<Ccodem> findByCIdWithValidDate(String cId, LocalDate targetDate);

    /**
     * 상위코드(HRK_C) 기준 자식 코드 역조회
     *
     * @param hrkC 상위코드 ({C_ID}_{CDVA} 합성 문자열)
     */
    List<Ccodem> findChildrenOfHrkC(String hrkC);

    /**
     * 코드타입(C_TP) 기준 다건 조회
     *
     * @param cTp        코드타입 (예: IOE_LEAFE, IOE_XPN)
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜)
     */
    List<Ccodem> findByCTpWithValidDate(String cTp, LocalDate targetDate);

    /**
     * 논리 삭제되지 않은 전체 공통코드를 코드순서 오름차순(null 마지막)으로 조회
     */
    List<Ccodem> findAllActive();
}
