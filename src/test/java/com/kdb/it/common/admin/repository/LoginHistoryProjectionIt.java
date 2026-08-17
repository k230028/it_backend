package com.kdb.it.common.admin.repository;

import static com.kdb.it.support.ProjectionContracts.declaredMethodNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class LoginHistoryProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired LoginHistoryRepository loginHistoryRepository;
    @Autowired EntityManager entityManager;

    @Test
    void 로그인이력프로젝션의실제매핑과최신순페이지계약을검증한다() {
        LocalDateTime base = LocalDateTime.of(2099, 12, 31, 23, 50);
        entityManager.persist(login("BE03-LOGIN-OLDER", base, "1", "10.0.0.1", null, "agent-old"));
        entityManager.persist(
                login(
                        "BE03-LOGIN-NEWER",
                        base.plusMinutes(1),
                        "2",
                        "10.0.0.2",
                        "BE03 실패",
                        "agent-new"));
        entityManager.flush();
        entityManager.clear();

        Page<LoginHistoryRepository.LoginHistoryView> page =
                loginHistoryRepository.findPageViewsByOrderByLgnDtmDesc(PageRequest.of(0, 2));

        assertThat(page.getContent())
                .extracting(
                        view -> view.getEno(),
                        view -> view.getLgnDtm(),
                        view -> view.getItPtlLgnTc(),
                        view -> view.getIpAddr(),
                        view -> view.getLgnErrRsn(),
                        view -> view.getAgtVrsCone(),
                        view -> view.getFstEnrDtm())
                .containsExactly(
                        tuple(
                                "BE03-LOGIN-NEWER",
                                base.plusMinutes(1),
                                "2",
                                "10.0.0.2",
                                "BE03 실패",
                                "agent-new",
                                base.plusMinutes(1)),
                        tuple("BE03-LOGIN-OLDER", base, "1", "10.0.0.1", null, "agent-old", base));
        assertThat(declaredMethodNames(LoginHistoryRepository.LoginHistoryView.class)).hasSize(7);
    }

    private Clognh login(
            String eno,
            LocalDateTime lgnDtm,
            String loginType,
            String ip,
            String error,
            String agent) {
        return Clognh.builder()
                .eno(eno)
                .lgnDtm(lgnDtm)
                .itPtlLgnTc(loginType)
                .ipAddr(ip)
                .lgnErrRsn(error)
                .agtVrsCone(agent)
                .fstEnrDtm(lgnDtm)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(lgnDtm)
                .lstChgUsid("BE03-TEST")
                .delYn("N")
                .build();
    }
}
