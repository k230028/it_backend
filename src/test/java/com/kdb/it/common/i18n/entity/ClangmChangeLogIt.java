package com.kdb.it.common.i18n.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.i18n.model.TranslationColumns;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import com.kdb.it.domain.log.listener.ApplicationContextHolder;
import com.kdb.it.domain.log.listener.AuditFailureRecorder;
import com.kdb.it.domain.log.listener.AuditLogPersister;
import com.kdb.it.domain.log.listener.AuditLogWriter;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 번역 마스터({@code TPRMPP_CLANGM}) 변경이 변경로그({@code TPRMPP_CLANGL})에 실제로 적재되는지 로컬 Oracle로 검증한다.
 *
 * <p>{@code AuditLogPersister}는 원 업무 트랜잭션 <b>커밋 이후</b>({@code afterCommit})에만 로그를 INSERT한다. 따라서
 * {@code @DataJpaTest}의 기본 롤백 트랜잭션 아래에서는 콜백이 영영 발화하지 않아 이 경로를 검증할 수 없다. 클래스 레벨
 * {@code @Transactional(NOT_SUPPORTED)}로 테스트 트랜잭션을 끄면 {@code saveAndFlush} 호출마다 리포지토리 자신의 트랜잭션이 실제로
 * 커밋되어 콜백이 동기로 실행된다.
 *
 * <p>{@code @DataJpaTest} 슬라이스는 {@code @Component}를 포함하지 않으므로 감사 경로의 협력 빈을 명시적으로 가져온다. {@code
 * SimpleMeterRegistry}는 {@link AuditFailureRecorder}가 요구하는 {@code MeterRegistry} 구현이다.
 *
 * <p>실제 커밋되는 만큼 테스트가 마스터 1건과 로그 3건을 실 DB에 남긴다. 대상 키를 UUID로 유일하게 만들고 {@link #tearDown()}에서 자신이 만든 행만
 * 물리 삭제한다(운영 코드는 논리 삭제만 사용하며 이 물리 삭제는 테스트 전용이다).
 */
@Import({
    ApplicationContextHolder.class,
    AuditLogPersister.class,
    AuditLogWriter.class,
    AuditFailureRecorder.class,
    SimpleMeterRegistry.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClangmChangeLogIt extends AbstractOracleRepositoryTest {

    /** 감사 변경자(CHG_USID)로 기록될 사번. CHG_USID는 14자 제한이다. */
    private static final String ACTOR = "ITEST01";

    /** 번역 대상 구분명 — 마스터의 {@code CK_CLANGM_DTT_COL}가 허용하는 (구분명, 컬럼명) 조합이다. */
    private static final String TARGET_NAME = "메뉴";

    @Autowired private ClangmRepository repository;

    @Autowired private JdbcTemplate jdbcTemplate;

    /** 실 데이터와 충돌하지 않는 이번 실행 전용 대상 키. */
    private String targetKey;

    @BeforeEach
    void setUp() {
        targetKey = "IT-I18N-LOG-" + UUID.randomUUID();
        // CHG_USID를 채우고 "인증 컨텍스트 없음" 경고 없이 감사 경로를 타도록 인증 주체를 심는다.
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                ACTOR, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        // 커밋된 로그·마스터 행을 남기지 않도록 자신이 만든 키만 정리한다(자식 로그 먼저).
        // DELETE 중 하나가 예외를 던져도 인증 주체가 후속 테스트로 새지 않도록 clearContext()는
        // finally에서 항상 실행한다.
        try {
            jdbcTemplate.update("DELETE FROM TPRMPP_CLANGL WHERE TC_ID_CONE = ?", targetKey);
            jdbcTemplate.update("DELETE FROM TPRMPP_CLANGM WHERE TC_ID_CONE = ?", targetKey);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("번역 생성·수정·논리삭제가 TPRMPP_CLANGL에 C/U/D 순서로 적재된다")
    void 번역_생성_수정_논리삭제가_각각_로그로_남는다() {
        Clangm created = repository.saveAndFlush(newTranslation("Dashboard"));

        created.update("Main Dashboard");
        Clangm updated = repository.saveAndFlush(created);

        updated.delete();
        repository.saveAndFlush(updated);

        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT CHG_DTT_YN, TC_DES, DEL_YN, CHG_USID, DTT_LAN_C, TC_COL_NM"
                                + " FROM TPRMPP_CLANGL WHERE TC_ID_CONE = ?"
                                + " ORDER BY LOG_HIS_TGR_SNO",
                        targetKey);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0))
                .containsEntry("CHG_DTT_YN", "C")
                .containsEntry("TC_DES", "Dashboard")
                .containsEntry("DEL_YN", "N")
                .containsEntry("CHG_USID", ACTOR)
                .containsEntry("DTT_LAN_C", "en")
                .containsEntry("TC_COL_NM", TranslationColumns.MNU_NM);
        assertThat(rows.get(1))
                .containsEntry("CHG_DTT_YN", "U")
                .containsEntry("TC_DES", "Main Dashboard")
                .containsEntry("DEL_YN", "N");
        assertThat(rows.get(2))
                .containsEntry("CHG_DTT_YN", "D")
                .containsEntry("TC_DES", "Main Dashboard")
                .containsEntry("DEL_YN", "Y");
    }

    /**
     * 저장 가능한 최소 번역 마스터 행을 만든다.
     *
     * <p>{@code @DataJpaTest} 슬라이스에는 JPA Auditing 설정이 없어 등록자·변경자가 자동으로 채워지지 않으므로, 물리 NOT NULL인 감사
     * 공통값을 직접 지정한다({@code ClangmRepositoryIt}와 같은 방식).
     *
     * @param text 번역 문구
     * @return 저장 대상 엔티티
     */
    private Clangm newTranslation(String text) {
        LocalDateTime now = LocalDateTime.now();
        return Clangm.builder()
                .tcIdCone(targetKey)
                .dttLanC("en")
                .tcColNm(TranslationColumns.MNU_NM)
                .tcDes(text)
                .dttNm(TARGET_NAME)
                .delYn("N")
                .guid(UUID.randomUUID().toString())
                .guidPrgSno(1)
                .fstEnrUsid(ACTOR)
                .fstEnrDtm(now)
                .lstChgUsid(ACTOR)
                .lstChgDtm(now)
                .build();
    }
}
