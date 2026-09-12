package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.ItApplication;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * {@code spring.task.scheduling.pool.size}가 실제 {@code @Scheduled} 작업 수 이상인지 지킨다.
 *
 * <p>풀이 작업 수보다 작으면 외부 전송에 막힌 작업이 다른 작업의 스레드를 빼앗아 TTL 복원 같은 작업이 영영 돌지 않는다. 새 스케줄 작업을 추가하면 이 테스트가
 * 깨지므로 {@code application.properties}의 풀 크기와 주석을 함께 갱신해야 한다.
 */
class SchedulingPoolSizeTest {

    private static final String POOL_SIZE_PROPERTY = "spring.task.scheduling.pool.size";

    @Test
    @DisplayName("@Scheduled 메서드 수는 application.properties의 스케줄링 풀 크기를 넘지 않는다")
    void scheduledMethodCount_doesNotExceedPoolSize() throws IOException {
        Properties properties =
                PropertiesLoaderUtils.loadProperties(
                        new ClassPathResource("application.properties"));
        String configured = properties.getProperty(POOL_SIZE_PROPERTY);
        assertThat(configured).as(POOL_SIZE_PROPERTY + " 미설정").isNotBlank();
        int poolSize = Integer.parseInt(configured.trim());

        List<String> scheduledMethods = scheduledMethodsInMainClasses();

        assertThat(scheduledMethods)
                .as("@Scheduled 작업 %s 이(가) 풀 크기 %d 를 넘습니다", scheduledMethods, poolSize)
                .hasSizeLessThanOrEqualTo(poolSize);
        assertThat(scheduledMethods).isNotEmpty();
    }

    /** 메인 소스셋({@link ItApplication}의 코드 위치)만 ASM 메타데이터로 스캔해 {@code @Scheduled} 메서드를 모은다. */
    private static List<String> scheduledMethodsInMainClasses() throws IOException {
        URL mainClasses = ItApplication.class.getProtectionDomain().getCodeSource().getLocation();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
        String pattern = mainClasses.toExternalForm();
        if (!pattern.endsWith("/")) {
            pattern += "/";
        }
        List<String> found = new ArrayList<>();
        for (Resource resource : resolver.getResources(pattern + "com/kdb/it/**/*.class")) {
            MetadataReader reader = readerFactory.getMetadataReader(resource);
            reader.getAnnotationMetadata()
                    .getAnnotatedMethods(Scheduled.class.getName())
                    .forEach(
                            method ->
                                    found.add(
                                            reader.getClassMetadata().getClassName()
                                                    + "#"
                                                    + method.getMethodName()));
        }
        return found;
    }
}
