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
 * 공통로그인이력 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CLOGNH}
 *
 * <p>사용자의 로그인, 로그인 실패, 로그아웃 이력을 기록합니다. 보안 감사(Security Audit) 및 이상 접근 탐지에 활용됩니다.
 *
 * <p>로그인구분코드({@code IT_PTL_LGN_TC})는 공통코드 {@code C_ID='IT_PTL_LGN_TC'} 기반 1자리 값입니다.
 *
 * <ul>
 *   <li>{@code 1}: 로그인 성공
 *   <li>{@code 2}: 로그인 실패 (비밀번호 불일치, 존재하지 않는 사번 등)
 *   <li>{@code 3}: 로그아웃
 * </ul>
 */
@Entity
@Table(name = "TPRMPP_CLOGNH", comment = "공통로그인이력")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Clognh extends BaseEntity {

    /** 로그인로그일련번호: 기본키. Oracle 시퀀스(SQ_TPRMPP_CLOGNH_1)로 자동 채번 */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_TPRMPP_CLOGNH_1")
    @SequenceGenerator(
            name = "SQ_TPRMPP_CLOGNH_1",
            sequenceName = "SQ_TPRMPP_CLOGNH_1",
            allocationSize = 1)
    @Column(name = "LGN_LOG_SNO", comment = "로그인로그일련번호")
    private Long lgnLogSno;

    /** 사원번호: 로그인을 시도한 사용자의 사번 로그인 실패의 경우 DB에 없는 사번도 기록될 수 있음 */
    @Column(name = "ENO", nullable = false, length = 32, comment = "사원번호")
    private String eno;

    /** 로그인오류사유: 로그인 실패 시 실패 원인 메시지 (최대 600자) 성공(1), 로그아웃(3)의 경우 null 예: "비밀번호 불일치", "존재하지 않는 사번" */
    @Column(name = "LGN_ERR_RSN", length = 600, comment = "로그인오류사유")
    private String lgnErrRsn;

    /** IP주소: 클라이언트의 실제 IP 주소 (최대 20자) */
    @Column(name = "IP_ADDR", length = 20, comment = "IP주소")
    private String ipAddr;

    /**
     * 로그인일시: 이벤트 발생 일시 (서버 기준 시각) 물리 컬럼 LGN_DTM은 Oracle DATE 타입(초 단위). 엔티티는 LocalDateTime으로 매핑되며
     * 나노초 정밀도는 저장되지 않음
     */
    @Column(name = "LGN_DTM", nullable = false, comment = "로그인일시")
    private LocalDateTime lgnDtm;

    /** 로그인구분코드: 공통코드 {@code C_ID='IT_PTL_LGN_TC'} 기반 1자리 값. 1=로그인 성공, 2=로그인 실패, 3=로그아웃 */
    @Column(name = "IT_PTL_LGN_TC", nullable = false, length = 1, comment = "IT포탈로그인구분코드")
    private String itPtlLgnTc;

    /** 에이전트버전내용: 클라이언트 브라우저/기기 정보 (최대 100자) */
    @Column(name = "AGT_VRS_CONE", length = 100, comment = "에이전트버전내용")
    private String agtVrsCone;

    /** 로그인 성공 코드값 (공통코드 IT_PTL_LGN_TC) */
    public static final String LOGIN_SUCCESS = "1";

    /** 로그인 실패 코드값 (공통코드 IT_PTL_LGN_TC) */
    public static final String LOGIN_FAILURE = "2";

    /** 로그아웃 코드값 (공통코드 IT_PTL_LGN_TC) */
    public static final String LOGOUT = "3";

    /**
     * 로그인 이력 감사자 폴백(fallback) 값
     *
     * <p>세 팩토리 모두 객체 생성 시점에는 이 값을 감사자로 명시 기록합니다. 다만 실제 저장(@PrePersist) 시점에 {@link
     * com.kdb.it.config.JpaAuditConfig}의 AuditorAware가 유효한 인증 사용자를 반환하면, Spring Data JPA의 {@code
     * AuditingEntityListener}가 {@code FST_ENR_USID}/{@code LST_CHG_USID}를 그 사용자의 사번으로
     * 덮어씁니다({@code @CreatedBy}/{@code @LastModifiedBy} 표준 동작). 즉 이 상수가 실제 DB에 남는지 여부는 저장 시점의 인증 상태에
     * 달려 있습니다:
     *
     * <ul>
     *   <li>{@link #createLoginSuccess}/{@link #createLoginFailure}: 로그인 성립 이전(비인증) 요청이므로 저장 시점에도
     *       AuditorAware가 항상 {@code Optional.empty()}를 반환 → 이 값이 그대로 저장됩니다.
     *   <li>{@link #createLogout}: {@code /api/auth/logout}의 정상 경로는 유효한 Access Token 인증이 필요하므로, 저장
     *       시점에 실제 로그아웃한 사용자의 사번으로 덮어써집니다. 이 값은 Refresh 쿠키만으로 폐기하는({@code logoutByRefreshToken})
     *       만료/비인증 경로에서만 그대로 저장됩니다.
     * </ul>
     */
    public static final String SYSTEM_AUDITOR = "SYSTEM";

    /**
     * 로그인 성공 이력 생성 정적 팩토리 메서드
     *
     * @param eno 로그인에 성공한 사용자의 사번
     * @param ipAddr 접속 IP 주소
     * @param agtVrsCone 접속 브라우저/기기 정보
     * @return 로그인 성공 이력 엔티티 ({@code itPtlLgnTc = "1"}, 감사자 = {@value #SYSTEM_AUDITOR})
     */
    public static Clognh createLoginSuccess(String eno, String ipAddr, String agtVrsCone) {
        Clognh clognh =
                Clognh.builder()
                        .eno(eno)
                        .itPtlLgnTc(LOGIN_SUCCESS)
                        .ipAddr(ipAddr)
                        .agtVrsCone(agtVrsCone)
                        .lgnDtm(LocalDateTime.now())
                        .build();
        clognh.initializeAuditActors(SYSTEM_AUDITOR);
        return clognh;
    }

    /**
     * 로그인 실패 이력 생성 정적 팩토리 메서드
     *
     * @param eno 로그인을 시도한 사번 (DB에 없는 사번일 수 있음)
     * @param ipAddr 접속 IP 주소
     * @param agtVrsCone 접속 브라우저/기기 정보
     * @param lgnErrRsn 실패 사유 (예: "비밀번호 불일치", "존재하지 않는 사번")
     * @return 로그인 실패 이력 엔티티 ({@code itPtlLgnTc = "2"}, 감사자 = {@value #SYSTEM_AUDITOR})
     */
    public static Clognh createLoginFailure(
            String eno, String ipAddr, String agtVrsCone, String lgnErrRsn) {
        Clognh clognh =
                Clognh.builder()
                        .eno(eno)
                        .itPtlLgnTc(LOGIN_FAILURE)
                        .ipAddr(ipAddr)
                        .agtVrsCone(agtVrsCone)
                        .lgnDtm(LocalDateTime.now())
                        .lgnErrRsn(lgnErrRsn)
                        .build();
        clognh.initializeAuditActors(SYSTEM_AUDITOR);
        return clognh;
    }

    /**
     * 로그아웃 이력 생성 정적 팩토리 메서드
     *
     * <p>객체 생성 시점의 감사자 폴백은 {@value #SYSTEM_AUDITOR}이지만, {@code /api/auth/logout}의 정상(인증됨) 경로에서는 저장
     * 시점에 {@code AuditingEntityListener}가 실제 로그아웃한 사용자의 사번으로 덮어씁니다 — {@link #SYSTEM_AUDITOR} 상세 참조.
     *
     * @param eno 로그아웃한 사용자의 사번
     * @param ipAddr 접속 IP 주소
     * @param agtVrsCone 접속 브라우저/기기 정보
     * @return 로그아웃 이력 엔티티 ({@code itPtlLgnTc = "3"}, 감사자 폴백 = {@value #SYSTEM_AUDITOR}, 인증된 로그아웃은
     *     저장 시 실제 사번으로 대체됨)
     */
    public static Clognh createLogout(String eno, String ipAddr, String agtVrsCone) {
        Clognh clognh =
                Clognh.builder()
                        .eno(eno)
                        .itPtlLgnTc(LOGOUT)
                        .ipAddr(ipAddr)
                        .agtVrsCone(agtVrsCone)
                        .lgnDtm(LocalDateTime.now())
                        .build();
        clognh.initializeAuditActors(SYSTEM_AUDITOR);
        return clognh;
    }
}
