package com.kdb.it.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.council.entity.Bpovwm;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

class EntityRestoreSupportTest {

    @Test
    void findDeletedRestoresExistingEntityWithoutChangingGuid() {
        EntityManager entityManager = mock(EntityManager.class);
        Bpovwm deleted =
                Bpovwm.builder()
                        .itPtlAsctId("ASCT-1")
                        .guid("existing-guid")
                        .guidPrgSno(1)
                        .delYn("Y")
                        .build();
        when(entityManager.find(Bpovwm.class, "ASCT-1")).thenReturn(deleted);

        Bpovwm restored = EntityRestoreSupport.findAndRestore(entityManager, Bpovwm.class, "ASCT-1");

        assertThat(restored).isSameAs(deleted);
        assertThat(restored.getDelYn()).isEqualTo("N");
        assertThat(restored.getGuid()).isEqualTo("existing-guid");
    }

    @Test
    void findDeletedReturnsNullWhenPrimaryKeyDoesNotExist() {
        EntityManager entityManager = mock(EntityManager.class);

        assertThat(EntityRestoreSupport.findAndRestore(entityManager, Bpovwm.class, "ASCT-1"))
                .isNull();
    }
}
