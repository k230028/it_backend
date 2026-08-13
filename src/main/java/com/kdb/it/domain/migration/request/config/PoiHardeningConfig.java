package com.kdb.it.domain.migration.request.config;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Apache POI의 JVM 전역 안전 한도를 기동 시 한 번만 설정합니다.
 *
 * <p>{@code ZipSecureFile.setMinInflateRatio}와 {@code IOUtils.setByteArrayMaxOverride}는 인스턴스가 아니라
 * **JVM 전역 정적 상태**를 바꿉니다. 이 호출을 파서 클래스 생성자에 두면 파서를 새로 만들 때마다 전역값이 덮어써져, 서로 다른 한도를 가진 인스턴스가 공존할 수 없고
 * 테스트 격리도 깨집니다(작은 한도로 만든 인스턴스 하나가 같은 JVM의 다른 모든 POI 사용처를 망가뜨립니다).
 *
 * <p>그래서 전역 한도는 여기서만 설정하고 {@code WorkbookReader}는 자기 인스턴스가 아는 한도만 검사합니다.
 */
@Configuration
public class PoiHardeningConfig {

    /**
     * 전역 한도를 적용합니다.
     *
     * @param minInflateRatio zip 압축 해제 최소 비율. `.xlsx` 압축 폭탄을 막습니다
     * @param maxFileBytes 단일 레코드 배열 할당 상한. `.xls`의 레코드 길이 필드를 신뢰해 생기는 거대 할당을 막습니다
     */
    public PoiHardeningConfig(
            @Value("${app.migration.request.min-inflate-ratio}") double minInflateRatio,
            @Value("${app.migration.request.max-file-bytes}") long maxFileBytes) {
        ZipSecureFile.setMinInflateRatio(minInflateRatio);
        IOUtils.setByteArrayMaxOverride((int) Math.min(maxFileBytes, Integer.MAX_VALUE));
    }
}
