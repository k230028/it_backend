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
 * 로그인이력 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_CLOGNH}
 * </p>
 *
 * <p>
 * 사용자의 로그인, 로그인 실패, 로그아웃 이력을 기록합니다.
 * 보안 감사(Security Audit) 및 이상 접근 탐지에 활용됩니다.
 * </p>
 *
 * <p>
 * 이력 유형({@code LGN_TP}):
 * </p>
 * <ul>
 * <li>{@code LOGIN_SUCCESS}: 로그인 성공</li>
 * <li>{@code LOGIN_FAILURE}: 로그인 실패 (비밀번호 불일치, 존재하지 않는 사번 등)</li>
 * <li>{@code LOGOUT}: 로그아웃</li>
 * </ul>
 */
@Entity
@Table(name = "TAAABB_CLOGNH", comment = "로그인이력")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Clognh extends BaseEntity {

    /**
     * 로그인일련번호: 기본키. Oracle 시퀀스(SEQ_CLOGNH)로 자동 채번
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_CLOGNH")
    @SequenceGenerator(name = "SEQ_CLOGNH", sequenceName = "SEQ_CLOGNH", allocationSize = 1)
    @Column(name = "LGN_SNO", comment = "로그인일련번호")
    private Long lgnSno;

    /**
     * 사원번호: 로그인을 시도한 사용자의 사번
     * 로그인 실패의 경우 DB에 없는 사번도 기록될 수 있음
     */
    @Column(name = "ENO", nullable = false, length = 32, comment = "사원번호")
    private String eno;

    /**
     * 로그인유형: 이력의 종류
     * 값: "LOGIN_SUCCESS", "LOGIN_FAILURE", "LOGOUT"
     */
    @Column(name = "LGN_TP", nullable = false, length = 80, comment = "로그인유형")
    private String lgnTp;

    /** IP주소: 클라이언트의 실제 IP 주소 (프록시 환경 고려, 최대 200자) */
    @Column(name = "IP_ADDR", length = 200, comment = "IP주소")
    private String ipAddr;

    /** 사용자에이전트: 클라이언트 브라우저/기기 정보 (최대 2000자) */
    @Column(name = "UST_AGT", length = 2000, comment = "사용자에이전트")
    private String ustAgt;

    /** 로그인일시: 이벤트 발생 일시 (서버 기준 시각) */
    @Column(name = "LGN_DTM", nullable = false, comment = "로그인일시")
    private LocalDateTime lgnDtm;

    /**
     * 실패사유: 로그인 실패 시 실패 원인 메시지 (최대 200자)
     * LOGIN_SUCCESS, LOGOUT의 경우 null
     * 예: "비밀번호 불일치", "존재하지 않는 사번"
     */
    @Column(name = "FLUR_RSN", length = 200, comment = "실패사유")
    private String flurRsn;

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGOUT        = "LOGOUT";

    /**
     * 로그인 성공 이력 생성 정적 팩토리 메서드
     *
     * @param eno    로그인에 성공한 사용자의 사번
     * @param ipAddr 접속 IP 주소
     * @param ustAgt 접속 브라우저/기기 정보
     * @return 로그인 성공 이력 엔티티 ({@code lgnTp = "LOGIN_SUCCESS"})
     */
    public static Clognh createLoginSuccess(String eno, String ipAddr, String ustAgt) {
        return Clognh.builder()
                .eno(eno)
                .lgnTp(LOGIN_SUCCESS)
                .ipAddr(ipAddr)
                .ustAgt(ustAgt)
                .lgnDtm(LocalDateTime.now())
                .build();
    }

    /**
     * 로그인 실패 이력 생성 정적 팩토리 메서드
     *
     * @param eno      로그인을 시도한 사번 (DB에 없는 사번일 수 있음)
     * @param ipAddr   접속 IP 주소
     * @param ustAgt   접속 브라우저/기기 정보
     * @param flurRsn  실패 사유 (예: "비밀번호 불일치", "존재하지 않는 사번")
     * @return 로그인 실패 이력 엔티티 ({@code lgnTp = "LOGIN_FAILURE"})
     */
    public static Clognh createLoginFailure(String eno, String ipAddr, String ustAgt, String flurRsn) {
        return Clognh.builder()
                .eno(eno)
                .lgnTp(LOGIN_FAILURE)
                .ipAddr(ipAddr)
                .ustAgt(ustAgt)
                .lgnDtm(LocalDateTime.now())
                .flurRsn(flurRsn)
                .build();
    }

    /**
     * 로그아웃 이력 생성 정적 팩토리 메서드
     *
     * @param eno    로그아웃한 사용자의 사번
     * @param ipAddr 접속 IP 주소
     * @param ustAgt 접속 브라우저/기기 정보
     * @return 로그아웃 이력 엔티티 ({@code lgnTp = "LOGOUT"})
     */
    public static Clognh createLogout(String eno, String ipAddr, String ustAgt) {
        return Clognh.builder()
                .eno(eno)
                .lgnTp(LOGOUT)
                .ipAddr(ipAddr)
                .ustAgt(ustAgt)
                .lgnDtm(LocalDateTime.now())
                .build();
    }
}
