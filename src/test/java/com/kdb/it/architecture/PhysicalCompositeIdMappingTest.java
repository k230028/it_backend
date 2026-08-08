package com.kdb.it.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bmqnam;
import com.kdb.it.domain.council.entity.Bpqnam;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.MainQnaRepository;
import com.kdb.it.domain.council.repository.QnaRepository;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;

class PhysicalCompositeIdMappingTest {

    @Test
    void entitiesMapEveryColumnOfThePhysicalPrimaryKey() {
        assertPhysicalId(Cappla.class, "apfDcmNo", "apfSno");
        assertPhysicalId(Bcmmtm.class, "itPtlAsctId", "itPtlAsctMebTc", "eno");
        assertPhysicalId(Bmqnam.class, "itPtlAsctId", "qtnId");
        assertPhysicalId(Bpqnam.class, "itPtlAsctId", "qtnId");
    }

    @Test
    void repositoriesUseTheEntityIdClass() {
        assertRepositoryIdClass(ApplicationMapRepository.class, "CapplaId");
        assertRepositoryIdClass(CommitteeRepository.class, "BcmmtmId");
        assertRepositoryIdClass(MainQnaRepository.class, "BmqnamId");
        assertRepositoryIdClass(QnaRepository.class, "BpqnamId");
    }

    private static void assertPhysicalId(Class<?> entityType, String... expectedFields) {
        assertThat(entityType.getAnnotation(IdClass.class)).isNotNull();
        Set<String> actual =
                Arrays.stream(entityType.getDeclaredFields())
                        .filter(field -> field.isAnnotationPresent(Id.class))
                        .map(Field::getName)
                        .collect(Collectors.toSet());
        assertThat(actual).containsExactlyInAnyOrder(expectedFields);
    }

    private static void assertRepositoryIdClass(
            Class<?> repositoryType, String expectedSimpleName) {
        Class<?> idType =
                ResolvableType.forClass(repositoryType)
                        .as(JpaRepository.class)
                        .getGeneric(1)
                        .resolve();
        assertThat(idType).isNotNull();
        assertThat(idType.getSimpleName()).isEqualTo(expectedSimpleName);
    }
}
