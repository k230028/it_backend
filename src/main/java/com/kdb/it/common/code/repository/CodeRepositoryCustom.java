package com.kdb.it.common.code.repository;

import com.kdb.it.common.code.entity.Ccodem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 공통코드 QueryDSL 커스텀 리포지토리 인터페이스 */
public interface CodeRepositoryCustom {

    /**
     * 코드ID + 코드값 + 기준일자로 단건 조회
     *
     * @param cId 코드ID (예: CUR, PRJ_TP)
     * @param cdva 코드값 (예: 001, STA)
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜)
     */
    Optional<Ccodem> findByCIdAndCdvaWithValidDate(String cId, String cdva, LocalDate targetDate);

    /**
     * 코드ID 기준 다건 조회 (카테고리 전체)
     *
     * @param cId 코드ID
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
     * @param cTp 코드타입 (예: IOE_LEAFE, IOE_XPN)
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜)
     */
    List<Ccodem> findByCTpWithValidDate(String cTp, LocalDate targetDate);

    /** 논리 삭제되지 않은 전체 공통코드를 코드순서 오름차순(null 마지막)으로 조회 */
    List<Ccodem> findAllActive();

    /**
     * 코드ID 기준 다건 조회 — REST 응답 전용 경량 프로젝션 (guid, guidPrgSno 제외)
     *
     * <p>{@link #findByCIdWithValidDate(String, LocalDate)}와 동일한 조건·정렬을 사용한다. {@code
     * CodeService.getCcodemsByCId} 전용이며 캐시 경로({@code findCodeEntitiesByCId})는 이 메서드를 사용하지 않는다.
     *
     * @param cId 코드ID
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜)
     */
    List<CcodemResponseRow> findResponseRowsByCIdWithValidDate(String cId, LocalDate targetDate);

    /**
     * 코드ID + 코드값 + 기준일자로 단건 조회 — REST 응답 전용 경량 프로젝션 (guid, guidPrgSno 제외)
     *
     * <p>{@link #findByCIdAndCdvaWithValidDate(String, String, LocalDate)}와 동일한 조건을 사용한다. {@code
     * CodeService.getCcodem} 전용이며 {@code getBudgetPeriod} 등 엔티티 소비 경로는 이 메서드를 사용하지 않는다.
     *
     * @param cId 코드ID
     * @param cdva 코드값
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜)
     */
    Optional<CcodemResponseRow> findResponseRowByCIdAndCdvaWithValidDate(
            String cId, String cdva, LocalDate targetDate);

    /**
     * 코드타입(C_TP) 기준 다건 조회 — REST 응답 전용 경량 프로젝션 (guid, guidPrgSno 제외)
     *
     * <p>{@link #findByCTpWithValidDate(String, LocalDate)}와 동일한 조건·정렬을 사용한다. {@code
     * CodeService.getCcodemsByCTp} 전용이다.
     *
     * @param cTp 코드타입 (예: IOE_LEAFE, IOE_XPN)
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜)
     */
    List<CcodemResponseRow> findResponseRowsByCTpWithValidDate(String cTp, LocalDate targetDate);
}
