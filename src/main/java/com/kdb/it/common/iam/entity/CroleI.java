package com.kdb.it.common.iam.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 역할관리(사용자↔자격등급 매핑) 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CROLEI}
 *
 * <p>사용자(ENO)와 자격등급(ATH_ID)을 N:M 관계로 연결하는 매핑 테이블입니다. 한 사용자가 여러 자격등급을 보유할 수 있습니다.
 *
 * <p>예시:
 *
 * <ul>
 *   <li>ENO=12345678, ATH_ID=ITPZZ001 (일반사용자)
 *   <li>ENO=12345678, ATH_ID=ITPZZ002 (기획통할담당자 — 동일 사번에 추가 자격등급)
 * </ul>
 */
@Entity
@Table(name = "TPRMPP_CROLEI", comment = "역할관리(사용자↔자격등급 매핑)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class CroleI extends BaseEntity {

    /** 복합 기본키: ATH_ID(권한ID) + ENO(사원번호) 동일 사용자가 여러 자격등급을 가질 수 있으므로 복합키로 구성 */
    @EmbeddedId private CroleIId id;

    /** 사용여부: 'Y'=사용, 'N'=미사용 (기본값: 'Y') */
    @Column(name = "USE_YN", length = 1, comment = "사용여부")
    private String useYn;

    // BaseEntity: DEL_YN, GUID, GUID_PRG_SNO, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID
    // 제공

    // -------------------------------------------------------------------------
    // 편의 메서드
    // -------------------------------------------------------------------------

    /**
     * 복합키에서 권한ID(자격등급) 추출
     *
     * @return 자격등급 ID (예: ITPZZ001)
     */
    public String getAthId() {
        return id.getAthId();
    }

    /**
     * 복합키에서 사원번호 추출
     *
     * @return 사원번호
     */
    public String getEno() {
        return id.getEno();
    }

    /**
     * 사용여부 업데이트 (Dirty Checking 활용)
     *
     * @param useYn 사용여부 ('Y'=사용, 'N'=미사용)
     */
    public void updateUseYn(String useYn) {
        this.useYn = useYn;
    }
}
