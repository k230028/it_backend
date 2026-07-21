package com.kdb.it.common.system.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 갱신토큰 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CRTOKM}
 *
 * <p>JWT Refresh Token을 DB에 저장하여 관리합니다. 사용자당 최대 1개의 Refresh Token이 유지됩니다 (로그인 시 기존 토큰 삭제 후 재생성).
 *
 * <p>토큰 수명:
 *
 * <ul>
 *   <li>Refresh Token: 7일 ({@code application.properties}의 {@code jwt.refresh-token-validity})
 * </ul>
 *
 * <p>사용 흐름:
 *
 * <ol>
 *   <li>로그인 성공 → Refresh Token 생성 및 DB 저장 (최초생성시간은 BaseEntity.fstEnrDtm 자동 기록)
 *   <li>Access Token 만료 → Refresh Token으로 새 Access Token 발급
 *   <li>로그아웃 → DB에서 Refresh Token 삭제
 * </ol>
 */
@Entity
@Table(name = "TPRMPP_CRTOKM", comment = "갱신토큰")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Crtokm extends BaseEntity {

    /**
     * 토큰일련번호: 기본키. Oracle 시퀀스(SQ_TPRMPP_CRTOKM_1)로 자동 채번. 물리 컬럼명은 LGN_LOG_SNO(메타표준 의미=로그인로그일련번호)이나,
     * 이 테이블에서는 갱신토큰의 일련번호로 사용함
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_TPRMPP_CRTOKM_1")
    @SequenceGenerator(
            name = "SQ_TPRMPP_CRTOKM_1",
            sequenceName = "SQ_TPRMPP_CRTOKM_1",
            allocationSize = 1)
    @Column(name = "LGN_LOG_SNO", comment = "토큰일련번호 (물리컬럼 LGN_LOG_SNO=메타표준 로그인로그일련번호)")
    private Long tokSno;

    /** API토큰내용: 운영 NOT NULL 계약을 충족하기 위해 Refresh Token 원문이 아닌 SHA-256 HEX 값을 저장합니다. */
    @Column(name = "API_TOK_CONE", nullable = false, length = 2000, comment = "API토큰내용")
    private String apiTokCone;

    /** 암호화갱신발행토큰내용: Refresh Token 원문 대신 조회에 사용하는 SHA-256 HEX 값 */
    @Column(name = "ECY_RNW_PUB_TOK_CONE", length = 900, comment = "암호화갱신발행토큰내용")
    private String ecyRnwPubTokCone;

    /** 사원번호: 이 토큰을 소유한 사용자의 사번 로그아웃 또는 재로그인 시 사번으로 기존 토큰 삭제에 사용 */
    @Column(name = "ENO", nullable = false, length = 32, comment = "사원번호")
    private String eno;

    /**
     * 종료일시: 이 Refresh Token이 유효한 마지막 일시 이 시각 이후에는 토큰이 만료된 것으로 간주. 물리 컬럼 END_DTM은 Oracle DATE 타입(초
     * 단위). 엔티티는 LocalDateTime으로 매핑됨
     */
    @Column(name = "END_DTM", nullable = false, comment = "종료일시")
    private LocalDateTime endDtm;

    /** 가족명 — 토큰패밀리(로그인 1회=1패밀리). 재사용 탐지 시 패밀리 단위 폐기 기준 */
    @Column(name = "FAM_NM", nullable = false, length = 100, comment = "가족명")
    private String famNm;

    /** 유효여부 — 'Y'=활성 토큰, 'N'=회전된 구 토큰. 'N' 토큰이 재제출되면 재사용(탈취)으로 판단 */
    @Column(name = "AVL_YN", nullable = false, length = 1, comment = "유효여부")
    private String avlYn;

    /**
     * 토큰 만료 여부 확인 메서드
     *
     * @return true이면 만료됨 (삭제 필요), false이면 유효함
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endDtm);
    }

    /** 회전 표식 — 신규 토큰 발급 후 이 토큰을 '회전됨(비활성)'으로 표시(삭제 대신 유지하여 재사용 탐지) */
    public void markRotated() {
        this.avlYn = "N";
    }

    /** 회전된(이미 사용된) 토큰인지 — AVL_YN='N' */
    public boolean isRotated() {
        return "N".equals(this.avlYn);
    }
}
