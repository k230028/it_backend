package com.kdb.it.common.iam.entity;

import jakarta.persistence.*;
import lombok.*;

import lombok.experimental.SuperBuilder;
import com.kdb.it.common.util.CustomPasswordEncoder;
import com.kdb.it.domain.entity.BaseEntity;

/**
 * 사용자(직원) 정보 엔티티
 *
 * <p>
 * DB 테이블: {@code TPRMPP_CUSERI}
 * </p>
 *
 * <p>
 * 시스템 사용자(직원)의 기본 정보와 인증 정보를 관리합니다.
 * Spring Security의 {@code UserDetails} 소스로 활용됩니다.
 * </p>
 *
 * <p>
 * 연관 관계:
 * </p>
 * <ul>
 * <li>{@link CorgnI}와 ManyToOne 관계 ({@code BBR_C} →
 * {@code PRLM_OGZ_C_CONE})</li>
 * <li>조인 컬럼 {@code insertable=false, updatable=false}: 읽기 전용 조인 (BBR_C 자체로
 * 저장)</li>
 * </ul>
 */
@Entity // JPA 엔티티로 등록
@Table(name = "TPRMPP_CUSERI", comment = "사용자(직원) 정보") // 매핑할 DB 테이블명
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
public class CuserI extends BaseEntity {

    /**
     * 행번(사번): 기본키. 직원을 고유하게 식별하는 사번
     * 로그인 ID로도 사용되며, JWT 토큰의 subject에 저장됩니다.
     */
    @Id
    @Column(name = "ENO", nullable = false, length = 32, comment = "사원번호")
    private String eno;

    /**
     * 사용자암호화패스워드: SHA-256으로 암호화된 비밀번호
     * {@link CustomPasswordEncoder}로 암호화하여 저장합니다.
     * 최대 64자 (SHA-256 + Base64 인코딩 결과)
     */
    @Column(name = "USR_ECY_PWD", length = 64, comment = "사용자암호화패스워드 (물리컬럼 USR_ECY_PWD=메타표준 사용자암호화비밀번호)")
    private String usrEcyPwd;

    /**
     * 부서코드: 직원이 소속된 부점의 코드
     * {@link CorgnI}의 {@code PRLM_OGZ_C_CONE}과 조인 키로 사용
     */
    @Column(name = "BBR_C", length = 3, comment = "부서코드 (물리컬럼 BBR_C=메타표준 부점코드)")
    private String bbrC;

    /**
     * 소속 조직 정보: BBR_C → PRLM_OGZ_C_CONE으로 조인하여 조직 상세 정보 접근
     *
     * <p>
     * {@code insertable=false, updatable=false}: 이 컬럼을 직접 INSERT/UPDATE하지 않고
     * {@code bbrC} 필드를 통해서만 관리 (읽기 전용 조인)
     * </p>
     * <p>
     * {@code FetchType.LAZY}: 실제 접근 시에만 조회 (N+1 방지를 위해 EntityGraph 사용)
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BBR_C", referencedColumnName = "PRLM_OGZ_C_CONE", insertable = false, updatable = false)
    private CorgnI organization;

    /**
     * 부점명 조회 편의 메서드
     *
     * <p>
     * {@link CorgnI} 연관 관계에서 부점명을 반환합니다.
     * 조직 정보가 없는 경우 null을 반환합니다.
     * </p>
     *
     * @return 소속 부점명 (예: "IT본부"), 조직 정보 없을 시 null
     */
    public String getBbrNm() {
        return organization != null ? organization.getBbrNm() : null;
    }

    /**
     * 상위조직코드 조회 편의 메서드
     *
     * <p>
     * {@link CorgnI} 연관 관계에서 상위조직코드를 반환합니다.
     * 조직 정보가 없는 경우 null을 반환합니다.
     * </p>
     *
     * @return 상위조직코드 (예: "001"), 조직 정보 없을 시 null
     */
    public String getPrlmHrkOgzCCone() {
        return organization != null ? organization.getPrlmHrkOgzCCone() : null;
    }

    /**
     * 상위조직명 조회 편의 메서드
     *
     * <p>
     * {@link CorgnI} 연관 관계의 상위조직({@code parentOrganization}) 부점명을 반환합니다.
     * 조직 정보 또는 상위조직 정보가 없는 경우 null을 반환합니다.
     * </p>
     *
     * @return 상위조직명 (예: "경영지원본부"), 상위조직 정보 없을 시 null
     */
    public String getPrlmHrkOgzCNm() {
        if (organization != null && organization.getParentOrganization() != null) {
            return organization.getParentOrganization().getBbrNm();
        }
        return null;
    }

    /** 연락처전화번호 */
    @Column(name = "CADR_TPN", length = 20, comment = "연락처전화번호")
    private String cadrTpn;

    /** 파견부점코드 */
    @Column(name = "DTC_BBR_C", length = 3, comment = "파견부점코드")
    private String dtcBbrC;

    /** 직무상세내용: 직원의 담당 업무 상세 설명 (최대 2000자) */
    @Column(name = "DTS_DTL_CONE", length = 2000, comment = "직무상세내용")
    private String dtsDtlCone;

    /** 전자우편주소명: 직원 이메일 주소 (최대 200자) */
    @Column(name = "ETR_MIL_ADDR_NM", length = 200, comment = "전자우편주소명")
    private String etrMilAddrNm;

    /** 내선번호: 사내 내선 전화번호 */
    @Column(name = "INLE_NO", length = 20, comment = "내선번호")
    private String inleNo;

    /** 직위코드 */
    @Column(name = "PT_C", length = 5, comment = "직위코드")
    private String ptC;

    /** 직위코드명 */
    @Column(name = "PT_C_NM", length = 200, comment = "직위코드명")
    private String ptCNm;

    /** 팀코드: 소속 팀의 코드 (최대 5자) */
    @Column(name = "TEM_C", length = 5, comment = "팀코드")
    private String temC;

    /** 팀명: 소속 팀의 명칭 (최대 100자) */
    @Column(name = "TEM_NM", length = 100, comment = "팀명")
    private String temNm;

    /** 사용자명: 직원 한글 이름 (최대 100자) */
    @Column(name = "USR_NM", length = 100, comment = "사용자명")
    private String usrNm;

    /** 사용자영문명: 직원 영문 이름 (최대 100자) */
    @Column(name = "USR_WREN_NM", length = 100, comment = "사용자영문명")
    private String usrWrenNm;

    /** 회사전화번호 (최대 20자) */
    @Column(name = "CPN_TPN", length = 20, comment = "회사전화번호")
    private String cpnTpn;

    /**
     * JPA 엔티티 최초 저장 전 콜백 메서드 재정의
     *
     * <p>
     * 부모 클래스({@link BaseEntity})의 {@code prePersist()}를 호출한 후
     * CuserI 엔티티 고유의 초기화 로직을 추가로 수행합니다.
     * </p>
     */
    @Override
    public void prePersist() {
        super.prePersist();
        // CuserI 엔티티 고유의 추가 초기화 로직 (필요 시 구현)
    }

    /**
     * 사용자 기본정보 업데이트 (Dirty Checking 활용)
     * 비밀번호는 별도 updatePassword()로 처리합니다.
     *
     * @param usrNm        사용자명
     * @param ptCNm        직위명
     * @param temC         팀코드
     * @param bbrC         부서코드
     * @param etrMilAddrNm 이메일
     * @param inleNo       내선번호
     * @param cpnTpn       휴대폰번호
     */
    public void update(String usrNm, String ptCNm, String temC, String bbrC,
                       String etrMilAddrNm, String inleNo, String cpnTpn) {
        this.usrNm        = usrNm;
        this.ptCNm        = ptCNm;
        this.temC         = temC;
        this.bbrC         = bbrC;
        this.etrMilAddrNm = etrMilAddrNm;
        this.inleNo       = inleNo;
        this.cpnTpn       = cpnTpn;
    }

    /**
     * 비밀번호 변경 메서드
     *
     * <p>
     * 암호화된 비밀번호로 업데이트합니다.
     * 반드시 {@link CustomPasswordEncoder}로 암호화한 값을 전달해야 합니다.
     * </p>
     *
     * @param password SHA-256으로 암호화된 비밀번호 문자열
     */
    public void updatePassword(String password) {
        this.usrEcyPwd = password;
    }
}
