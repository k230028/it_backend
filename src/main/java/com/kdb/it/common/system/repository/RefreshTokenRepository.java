package com.kdb.it.common.system.repository;

import com.kdb.it.common.system.entity.Crtokm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 갱신토큰(Crtokm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 갱신토큰 테이블(TPRMPP_CRTOKM)에 대한 CRUD 기능을 제공합니다.</p>
 *
 * <p>기본키 타입: {@link Long} (tokSno: Oracle 시퀀스 SEQ_CRTOKM)</p>
 *
 * <p>Refresh Token 관리 전략:</p>
 * <ul>
 *   <li>사용자당 1개의 Refresh Token 유지</li>
 *   <li>로그인 시: 기존 토큰 삭제({@link #deleteByEno}) → 새 토큰 저장</li>
 *   <li>로그아웃 시: 해당 사용자의 토큰 삭제({@link #deleteByEno})</li>
 * </ul>
 */
public interface RefreshTokenRepository extends JpaRepository<Crtokm, Long> {

    /**
     * 토큰내용으로 갱신토큰 조회
     *
     * <p>클라이언트가 전달한 Refresh Token 값으로 DB에서 토큰 정보를 조회합니다.
     * Access Token 갱신 시 토큰 유효성 검사에 사용됩니다.</p>
     *
     * @param tokCone Refresh Token 문자열 (JWT 형식)
     * @return 해당 토큰 엔티티 (없으면 {@link Optional#empty()})
     */
    Optional<Crtokm> findByTokCone(String tokCone);

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

    /**
     * 토큰내용으로 갱신토큰 삭제
     *
     * <p>특정 토큰 값을 직접 삭제합니다. (현재 직접 사용하지 않으나 예비 메서드)</p>
     *
     * @param tokCone 삭제할 Refresh Token 문자열
     */
    void deleteByTokCone(String tokCone);
}
