package com.kdb.it.domain.budget.work.repository;

import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.entity.BbugtmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 예산(BBUGTM) 데이터 접근 리포지토리
 *
 * <p>
 * Spring Data JPA 기본 CRUD 및 커스텀 조회(QueryDSL) 메서드를 제공합니다.
 * </p>
 *
 * // Design Ref: §4.5 — BbugtmRepository
 */
public interface BbugtmRepository extends JpaRepository<Bbugtm, BbugtmId>, BbugtmRepositoryCustom {

    /**
     * Oracle 시퀀스로 예산관리번호 채번
     *
     * <p>
     * 형식: BG-{예산년도}-{4자리 시퀀스} (예: BG-2026-0001)
     * </p>
     *
     * @param bgYy 예산년도
     * @return 채번된 예산관리번호
     */
    @Query(value =
        "SELECT 'BG-' || :bgYy || '-' || LPAD(SEQ_BBUGTM.NEXTVAL, 4, '0') FROM DUAL",
        nativeQuery = true)
    String generateBgMngNo(@Param("bgYy") String bgYy);

    /**
     * 특정 연도의 편성 데이터 조회
     *
     * @param bseYy 예산년도
     * @param delYn 삭제여부 ('N')
     * @return 해당 연도의 편성 데이터 목록
     */
    List<Bbugtm> findByBseYyAndDelYn(String bseYy, String delYn);

    /**
     * 연도·원천테이블 단위 편성예산 전체 조회 (편성률 적용 시 존재확인 N+1 제거용)
     *
     * @param bseYy    회계연도
     * @param fntTbNm  원천테이블명 ("BCOSTM" 또는 "BITEMM")
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 해당 연도·테이블의 미삭제 편성예산 목록 (호출자가 pkColNm+sno+ioeC로 그룹핑)
     */
    List<Bbugtm> findByBseYyAndFntTbNmAndDelYn(String bseYy, String fntTbNm, String delYn);

    /**
     * Upsert용: 원본 기준으로 기존 편성 데이터 조회
     *
     * <p>
     * (BG_YY, ORC_TB, ORC_PK_VL, ORC_SNO_VL, IOE_C) 조합으로
     * 동일한 편성 레코드가 이미 존재하는지 확인합니다.
     * 존재하면 UPDATE, 없으면 INSERT (Upsert 패턴).
     * </p>
     *
     * // Plan SC: SC-05 — Upsert 동작 (중복 INSERT 방지)
     *
     * @param bseYy       예산년도
     * @param fntTbNm     원본테이블 (BPROJM/BCOSTM)
     * @param pkColNm     원본PK값
     * @param fntTbCrySno 원본일련번호값
     * @param ioeC     비목코드
     * @param delYn    삭제여부 ('N')
     * @return 기존 편성 데이터 (없으면 Optional.empty)
     */
    Optional<Bbugtm> findByBseYyAndFntTbNmAndPkColNmAndFntTbCrySnoAndIoeCAndDelYn(
        String bseYy, String fntTbNm, String pkColNm, Integer fntTbCrySno,
        String ioeC, String delYn);

    /**
     * 특정 예산관리번호 내 최대 일련번호 조회 (BG_SNO 채번용)
     *
     * @param bgNo 예산관리번호
     * @return 최대 일련번호 (없으면 null)
     */
    @Query("SELECT MAX(b.sno) FROM Bbugtm b WHERE b.bgNo = :bgNo")
    Integer findMaxSnoByBgNo(@Param("bgNo") String bgNo);

    /**
     * 특정 연도 + 원본테이블 + 원본PK 기준 편성 데이터 목록 조회
     *
     * <p>사업별 편성률 재적용 시 구버전 품목으로 인해 생성된 고아(orphan) BBUGTM 레코드를
     * 탐지·정리하기 위해 사용합니다.</p>
     *
     * @param bseYy   예산년도
     * @param fntTbNm 원본테이블 (BPROJM/BCOSTM/BITEMM)
     * @param pkColNm 원본PK값
     * @param delYn   삭제여부 ('N')
     * @return 해당 조건의 편성 데이터 목록
     */
    List<Bbugtm> findByBseYyAndFntTbNmAndPkColNmAndDelYn(
        String bseYy, String fntTbNm, String pkColNm, String delYn);
}
