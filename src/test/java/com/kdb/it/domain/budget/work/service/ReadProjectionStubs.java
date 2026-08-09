package com.kdb.it.domain.budget.work.service;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BudgetReadView;
import java.math.BigDecimal;

/** 기존 엔티티 픽스처를 읽기 프로젝션 계약으로 노출하는 단위 테스트 어댑터. */
final class ReadProjectionStubs {

    private ReadProjectionStubs() {}

    static BudgetReadView budget(Bbugtm entity) {
        return new BudgetReadView() {
            public String getBgNo() {
                return entity.getBgNo();
            }

            public Integer getSno() {
                return entity.getSno();
            }

            public String getPkColNm() {
                return entity.getPkColNm();
            }

            public String getFntTbNm() {
                return entity.getFntTbNm();
            }

            public String getIoeC() {
                return entity.getIoeC();
            }

            public BigDecimal getBgDupAmt() {
                return entity.getBgDupAmt();
            }

            public Integer getAsgRt() {
                return entity.getAsgRt();
            }
        };
    }

    static ProjectRepository.ProjectKeyView project(Bprojm entity) {
        return new ProjectRepository.ProjectKeyView() {
            public String getAbusMngNo() {
                return entity.getAbusMngNo();
            }

            public String getAbusNm() {
                return entity.getAbusNm();
            }
        };
    }
}
