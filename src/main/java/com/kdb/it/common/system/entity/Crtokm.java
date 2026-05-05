package com.kdb.it.common.system.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 갱신토큰 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_CRTOKM}
 * </p>
 *
 * <p>
 * JWT Refresh Token을 DB에 저장하여 관리합니다.
 * 사용자당 최대 1개의 Refresh Token이 유지됩니다 (로그인 시 기존 토큰 삭제 후 재생성).
 * </p>
 *
 * <p>
 * 토큰 수명:
 * </p>
 * <ul>
 * <li>Refresh Token: 7일 ({@code application.properties}의
 * {@code jwt.refresh-token-validity})</li>
 * </ul>
 *
 * <p>
 * 사용 흐름:
 * </p>
 * <ol>
 * <li>로그인 성공 → Refresh Token 생성 및 DB 저장 (최초생성시간은 BaseEntity.fstEnrDtm 자동 기록)</li>
 * <li>Access Token 만료 → Refresh Token으로 새 Access Token 발급</li>
 * <li>로그아웃 → DB에서 Refresh Token 삭제</li>
 * </ol>
 */
@Entity
@Table(name = "TAAABB_CRTOKM", comment = "갱신토큰")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Crtokm extends BaseEntity {

    /**
     * 토큰일련번호: 기본키. Oracle 시퀀스(S_TOK_SNO)로 자동 채번
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "S_TOK_SNO")
    @SequenceGenerator(name = "S_TOK_SNO", sequenceName = "S_TOK_SNO", allocationSize = 1)
    @Column(name = "TOK_SNO", comment = "토큰일련번호")
    private Long tokSno;

    /**
     * 토큰: JWT Refresh Token 값 (최대 2000자)
     * UNIQUE 제약조건으로 중복 저장 방지
     */
    @Column(name = "TOK", nullable = false, unique = true, length = 2000, comment = "토큰")
    private String tok;

    /**
     * 사원번호: 이 토큰을 소유한 사용자의 사번
     * 로그아웃 또는 재로그인 시 사번으로 기존 토큰 삭제에 사용
     */
    @Column(name = "ENO", nullable = false, length = 80, comment = "사원번호")
    private String eno;

    /**
     * 종료일시: 이 Refresh Token이 유효한 마지막 일시
     * 이 시각 이후에는 토큰이 만료된 것으로 간주
     */
    @Column(name = "END_DTM", nullable = false, comment = "종료일시")
    private LocalDateTime endDtm;

    /**
     * 토큰 만료 여부 확인 메서드
     *
     * @return true이면 만료됨 (삭제 필요), false이면 유효함
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endDtm);
    }

}

