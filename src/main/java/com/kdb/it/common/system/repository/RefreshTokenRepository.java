package com.kdb.it.common.system.repository;

import com.kdb.it.common.system.entity.Crtokm;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

/**
 * 갱신토큰(Crtokm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 갱신토큰 테이블(TPRMPP_CRTOKM)에 대한 CRUD 기능을 제공합니다.</p>
 *
 * <p>기본키 타입: {@link Long} (tokSno: Oracle 시퀀스 SQ_TPRMPP_CRTOKM_1)</p>
 *
 * <p>Refresh Token 관리 전략:</p>
 * <ul>
 *   <li>로그인 시: 기존 토큰 삭제({@link #deleteByEno}) → 새 패밀리 토큰 저장</li>
 *   <li>회전(refresh) 시: 구 토큰을 '회전됨(AVL_YN=N)'으로 유지하고 신규 토큰을 추가하므로,
 *       한 세션 안에서는 활성 토큰 + 회전된 토큰 등 2행 이상이 일시 공존할 수 있음(재사용 탐지용).</li>
 *   <li>로그아웃 시: 해당 사용자의 토큰 일괄 삭제({@link #deleteByEno})</li>
 *   <li>즉, '사용자당 1개'가 아니라 '사용자당 1패밀리'이며, 로그인/로그아웃 시 정리됨.</li>
 * </ul>
 */
public interface RefreshTokenRepository extends JpaRepository<Crtokm, Long> {

    /**
     * 암호화갱신발행토큰내용으로 Refresh Token을 조회합니다.
     *
     * @param ecyRnwPubTokCone Refresh Token SHA-256 HEX 조회값
     * @return 해당 토큰 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Crtokm> findByEcyRnwPubTokCone(String ecyRnwPubTokCone);

    /**
     * 패밀리명과 유효여부로 Refresh Token 목록을 조회합니다.
     *
     * @param famNm 패밀리명
     * @param avlYn 유효여부 ('Y'=활성, 'N'=회전됨)
     * @return 조건에 맞는 토큰 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Crtokm> findByFamNmAndAvlYn(String famNm, String avlYn);

    /**
     * 사번으로 갱신토큰 조회
     *
     * <p>특정 사용자의 Refresh Token을 조회합니다.
     * 재로그인 시 기존 토큰 갱신에 사용할 수 있습니다.</p>
     *
     * @param eno 사용자 사번
     * @return 해당 사번의 갱신토큰 엔티티 (없으면 {@link Optional#empty()})
     */
    Optional<Crtokm> findByEno(String eno);

    /**
     * 사번으로 갱신토큰 삭제
     *
     * <p>로그아웃 또는 재로그인 시 기존 Refresh Token을 DB에서 삭제합니다.
     * {@code @Transactional}이 필요하므로 호출하는 서비스 메서드에 설정해야 합니다.</p>
     *
     * @param eno 삭제할 토큰의 사용자 사번
     */
    void deleteByEno(String eno);

}
