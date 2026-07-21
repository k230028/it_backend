package com.kdb.it.common.admin.repository;

import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired FileRepository fileRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired EntityManager entityManager;

    @Test
    void 관리자파일과토큰프로젝션의필드와필터계약을검증한다() {
        LocalDateTime registeredAt = LocalDateTime.of(2026, 7, 21, 13, 0);
        entityManager.persist(file("FL_BE03_ADMIN_ACTIVE", "N", registeredAt));
        entityManager.persist(file("FL_BE03_ADMIN_DELETED", "Y", registeredAt.plusMinutes(1)));
        entityManager.persist(Crtokm.builder()
                .apiTokCone("API-TOKEN-SENTINEL-BE03")
                .ecyRnwPubTokCone("BE03-ADMIN-LOOKUP-UNIQUE")
                .eno("BE03-ADMIN-TOKEN")
                .endDtm(registeredAt.plusDays(7))
                .famNm("BE03-ADMIN-FAMILY")
                .avlYn("Y")
                .fstEnrDtm(registeredAt)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(registeredAt)
                .lstChgUsid("BE03-TEST")
                .delYn("N")
                .build());
        entityManager.flush();
        entityManager.clear();

        List<FileRepository.AdminFileView> files =
                fileRepository.findAdminFileViewsByDelYn("N").stream()
                        .filter(view -> view.getFlMpnId().startsWith("FL_BE03_ADMIN_"))
                        .toList();
        RefreshTokenRepository.AdminTokenView token = refreshTokenRepository.findAllProjectedBy().stream()
                .filter(view -> "BE03-ADMIN-TOKEN".equals(view.getEno()))
                .findFirst()
                .orElseThrow();

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFlMpnId()).isEqualTo("FL_BE03_ADMIN_ACTIVE");
        assertThat(files.getFirst().getFlNm()).isEqualTo("BE03 관리자 문서.pdf");
        assertThat(files.getFirst().getFlTpCone()).isEqualTo("첨부파일");
        assertThat(files.getFirst().getPkColNm()).isEqualTo("BE03-ADMIN-PARENT");
        assertThat(files.getFirst().getFstEnrDtm()).isEqualTo(registeredAt);
        assertThat(files.getFirst().getFstEnrUsid()).isEqualTo("BE03-TEST");
        assertThat(token.getEno()).isEqualTo("BE03-ADMIN-TOKEN");
        assertThat(token.getEndDtm()).isEqualTo(registeredAt.plusDays(7));
        assertThat(token.getEcyRnwPubTokCone()).isEqualTo("BE03-ADMIN-LOOKUP-UNIQUE");
        assertThat(token.getFstEnrDtm()).isEqualTo(registeredAt);
        assertThat(FileRepository.AdminFileView.class.getDeclaredMethods()).hasSize(6);
        assertThat(RefreshTokenRepository.AdminTokenView.class.getDeclaredMethods()).hasSize(4);
        assertThat(RefreshTokenRepository.AdminTokenView.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("getApiTokCone"));
    }

    private Cfilem file(String id, String delYn, LocalDateTime registeredAt) {
        return Cfilem.builder()
                .flMpnId(id)
                .flNm("BE03 관리자 문서.pdf")
                .flPysNm(id + ".pdf")
                .flKpnPth("/be03/admin")
                .flTpCone("첨부파일")
                .pkColNm("BE03-ADMIN-PARENT")
                .pkCone("BE03-ADMIN-KEY")
                .fstEnrDtm(registeredAt)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(registeredAt)
                .lstChgUsid("BE03-TEST")
                .delYn(delYn)
                .build();
    }
}
