package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AuditEntityIdentifier 단위 테스트.
 *
 * <p>단일·다중·복합 PK, 미할당+guid, 완전 미할당의 다섯 경우에 대한 식별자 문자열을 검증한다.
 */
class AuditEntityIdentifierTest {

    @Test
    @DisplayName("resolve - 단일 @Id는 field=value 로 반환한다")
    void resolve_singleId() {
        assertThat(AuditEntityIdentifier.resolve(new SingleId())).isEqualTo("id=5");
    }

    @Test
    @DisplayName("resolve - 다중 @Id는 선언 순서로 결합한다")
    void resolve_multiId() {
        assertThat(AuditEntityIdentifier.resolve(new MultiId())).isEqualTo("a=A,b=2");
    }

    @Test
    @DisplayName("resolve - @EmbeddedId는 내부 필드를 펼쳐 결합한다")
    void resolve_embeddedId() {
        assertThat(AuditEntityIdentifier.resolve(new EmbeddedIdEntity())).isEqualTo("x=X1,y=7");
    }

    @Test
    @DisplayName("resolve - @Id 미할당이면 guid로 대체한다")
    void resolve_unassignedIdFallbackGuid() {
        assertThat(AuditEntityIdentifier.resolve(new UnassignedWithGuid())).isEqualTo("guid=G-1");
    }

    @Test
    @DisplayName("resolve - @Id·guid 모두 없으면 <unavailable>")
    void resolve_allUnassigned() {
        assertThat(AuditEntityIdentifier.resolve(new AllUnassigned())).isEqualTo("<unavailable>");
    }

    // ── 테스트 픽스처 ──

    static class SingleId {
        @Id Long id = 5L;
    }

    static class MultiId {
        @Id String a = "A";
        @Id Integer b = 2;
    }

    static class EmbeddedIdEntity {
        @EmbeddedId Key key = new Key("X1", 7);
    }

    static class Key {
        String x;
        Integer y;

        Key(String x, Integer y) {
            this.x = x;
            this.y = y;
        }
    }

    static class UnassignedWithGuid {
        @Id Long id;
        String guid = "G-1";
    }

    static class AllUnassigned {
        @Id Long id;
    }
}
