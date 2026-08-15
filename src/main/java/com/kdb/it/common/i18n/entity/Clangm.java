package com.kdb.it.common.i18n.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 메뉴와 공통코드의 다국어 명칭을 저장하는 번역 마스터 엔티티입니다. */
@Entity
@Table(name = "TPRMPP_CLANGM", comment = "언어별구분코드마스터")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@IdClass(ClangmId.class)
public class Clangm extends BaseEntity {

    /** 메뉴 ID 또는 길이-prefix로 구성한 공통코드 복합키입니다. */
    @Id
    @Column(name = "TC_ID_CONE", nullable = false, length = 255, comment = "구분코드ID내용")
    private String tcIdCone;

    /** 소문자 언어코드입니다. */
    @Id
    @Column(name = "DTT_LAN_C", nullable = false, length = 2, comment = "구분언어코드")
    private String dttLanC;

    /** 번역 대상 원본 컬럼명입니다. */
    @Id
    @Column(name = "TC_COL_NM", nullable = false, length = 255, comment = "구분코드컬럼명")
    private String tcColNm;

    /** 번역된 표시 문구입니다. */
    @Column(name = "TC_DES", nullable = false, length = 2000, comment = "구분코드설명")
    private String tcDes;

    /** 번역 대상 구분명입니다. */
    @Column(name = "DTT_NM", nullable = false, length = 100, comment = "구분명")
    private String dttNm;

    /** 번역 문구를 변경하고 논리 삭제된 행을 복원합니다. */
    public void update(String tcDes) {
        this.tcDes = tcDes;
        restore();
    }
}
