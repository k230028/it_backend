package com.kdb.it.domain.menu.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CmenuaId implements Serializable {
    private String mnuId;
    private String athId;
}
