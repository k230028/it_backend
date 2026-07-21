package com.kdb.it.domain.menu.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
/** 메뉴 ID와 권한 ID로 구성된 메뉴 권한 매핑의 JPA 복합키입니다. */
public class CmenuaId implements Serializable {
    private String mnuId;
    private String athId;
}
