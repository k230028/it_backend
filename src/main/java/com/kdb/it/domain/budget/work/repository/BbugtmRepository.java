package com.kdb.it.domain.budget.work.repository;

import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.entity.BbugtmId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 예산(BBUGTM) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA 기본 CRUD 및 커스텀 조회(QueryDSL) 메서드를 제공합니다. // Design Ref: §4.5 — BbugtmRepository
 */
public interface BbugtmRepository extends JpaRepository<Bbugtm, BbugtmId>, BbugtmRepositoryCustom {

    /**
     * 예산관리번호 채번용 Oracle 시퀀스 원값을 가져옵니다.
     *
     * <p>번호 조립은 호출자(서비스)가 {@code String.format("BG-%s-%04d", ...)}로 수행합니다. 종전에는 이 쿼리가 {@code
     * LPAD(NEXTVAL, 4, '0')}으로 문자열까지 만들었으나, Oracle {@code LPAD}는 자릿수를 넘는 값을 **잘라내므로**({@code
     * LPAD(10000,4,'0')='1000'}) 시퀀스가 9,999를 넘는 순간 이미 존재하는 번호와 조용히 충돌한다. Java {@code
     * String.format}은 자르지 않고 자릿수가 늘어나 같은 사고가 나지 않으며, 다른 14개 채번과도 형태가 같아진다(BE-28, {@code CLAUDE.md}
     * §2).
     *
     * @return 다음 시퀀스 값
     */
    @Query(value = "SELECT SQ_TPRMPP_BBUGTM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long nextBgMngNoSeq();

    /**
     * 특정 연도의 편성 데이터 조회
     *
     * @param bseYy 예산년도
     * @param delYn 삭제여부 ('N')
     * @return 해당 연도의 편성 데이터 목록
     */
    List<Bbugtm> findByBseYyAndDelYn(String bseYy, String delYn);

    /**
     * 해당 예산년도의 미삭제(DEL_YN='N') 편성예산 전체를 벌크 Soft Delete 한다.
     *
     * <p>사업별 편성률 재적용(applyItemRates)의 "선 정리 → 후 재삽입" 패턴에서, 기존 "전체 메모리 로드 + 루프 delete()"를 단일 벌크
     * UPDATE로 대체합니다(P1 #1). 변경자 사번(LST_CHG_USID)과 변경일시(LST_CHG_DTM)를 UPDATE 문에서 직접 세팅합니다.
     *
     * <p><b>감사로그 트레이드오프</b>: 벌크 UPDATE는 JPA @PreUpdate→ChangeLogEntityListener를 우회하므로 이 선정리 구간의 행별
     * BbugtL 변경로그는 생성되지 않습니다. 본 구간은 직후 전량 재삽입되는 과도적 선정리라 행별 로그 가치가 낮다고 보고 손실을 수용합니다 (설계 §4.2
     * DECISION, 2026-06-29 확정).
     *
     * <p>{@code clearAutomatically=true}: 벌크 후 영속성 컨텍스트 1차 캐시를 비워, 직후 재삽입 로직이 stale 엔티티를 보지 않도록
     * 합니다. {@code flushAutomatically=true}: 선행 변경을 DB에 반영한 뒤 UPDATE를 실행합니다.
     *
     * @param bgYy 예산년도 (BSE_YY)
     * @param usid 변경자 사번 (LST_CHG_USID에 기록)
     * @param now 변경일시 (LST_CHG_DTM에 기록)
     * @return 영향받은(soft-delete된) 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE Bbugtm b SET b.delYn = 'Y', b.lstChgUsid = :usid, b.lstChgDtm = :now "
                    + "WHERE b.bseYy = :bgYy AND b.delYn = 'N'")
    int softDeleteByBseYy(
            @Param("bgYy") String bgYy,
            @Param("usid") String usid,
            @Param("now") LocalDateTime now);

    /**
     * 연도·원천테이블 단위 편성예산 전체 조회 (편성률 적용 시 존재확인 N+1 제거용)
     *
     * @param bseYy 회계연도
     * @param fntTbNm 원천테이블명 ("BCOSTM" 또는 "BITEMM")
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 해당 연도·테이블의 미삭제 편성예산 목록 (호출자가 pkColNm+sno+ioeC로 그룹핑)
     */
    List<Bbugtm> findByBseYyAndFntTbNmAndDelYn(String bseYy, String fntTbNm, String delYn);

    /**
     * Upsert용: 원본 기준으로 기존 편성 데이터 조회
     *
     * <p>(BG_YY, ORC_TB, ORC_PK_VL, ORC_SNO_VL, IOE_C) 조합으로 동일한 편성 레코드가 이미 존재하는지 확인합니다. 존재하면
     * UPDATE, 없으면 INSERT (Upsert 패턴). // Plan SC: SC-05 — Upsert 동작 (중복 INSERT 방지)
     *
     * @param bseYy 예산년도
     * @param fntTbNm 원본테이블 (BPROJM/BCOSTM)
     * @param pkColNm 원본PK값
     * @param fntTbCrySno 원본일련번호값
     * @param ioeC 비목코드
     * @param delYn 삭제여부 ('N')
     * @return 기존 편성 데이터 (없으면 Optional.empty)
     */
    Optional<Bbugtm> findByBseYyAndFntTbNmAndPkColNmAndFntTbCrySnoAndIoeCAndDelYn(
            String bseYy,
            String fntTbNm,
            String pkColNm,
            Integer fntTbCrySno,
            String ioeC,
            String delYn);

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
     * <p>사업별 편성률 재적용 시 구버전 품목으로 인해 생성된 고아(orphan) BBUGTM 레코드를 탐지·정리하기 위해 사용합니다.
     *
     * @param bseYy 예산년도
     * @param fntTbNm 원본테이블 (BPROJM/BCOSTM/BITEMM)
     * @param pkColNm 원본PK값
     * @param delYn 삭제여부 ('N')
     * @return 해당 조건의 편성 데이터 목록
     */
    List<Bbugtm> findByBseYyAndFntTbNmAndPkColNmAndDelYn(
            String bseYy, String fntTbNm, String pkColNm, String delYn);
}
