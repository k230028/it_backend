package com.kdb.it.common.admin.service;

import com.kdb.it.common.admin.dto.AdminLogDto;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.log.entity.*;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 상세 로그 조회 서비스.
 *
 * <p>로그 엔티티는 변경 이력 저장 전용이므로, 허용된 엔티티 목록을 기준으로
 * 공통 조회·상세 조회 기능만 제공합니다.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLogService {

    private static final int MAX_PAGE_SIZE = 500;

    private final EntityManager entityManager;
    private final UserRepository userRepository;

    private final Map<String, LogDefinition> definitions = buildDefinitions();

    /**
     * 조회 가능한 상세 로그 테이블 목록을 반환합니다.
     *
     * @return 허용된 로그 키·표시명·설명 목록
     */
    public List<AdminLogDto.LogTableResponse> getTables() {
        return definitions.values().stream()
                .map(this::toTableResponse)
                .toList();
    }

    /**
     * 특정 로그 테이블의 행 목록을 페이지 단위로 조회합니다.
     *
     * @param key      로그 테이블 키
     * @param pageable 페이지 정보
     * @return 로그 목록과 컬럼 메타 정보
     */
    public AdminLogDto.LogPageResponse getLogs(String key, Pageable pageable) {
        LogDefinition def = getDefinition(key);
        Pageable safePageable = safePageable(pageable);
        String entityName = def.entityClass().getSimpleName();

        List<?> entities = entityManager
                .createQuery("select e from " + entityName + " e order by e.logSno desc", def.entityClass())
                .setFirstResult((int) safePageable.getOffset())
                .setMaxResults(safePageable.getPageSize())
                .getResultList();

        long total = entityManager
                .createQuery("select count(e) from " + entityName + " e", Long.class)
                .getSingleResult();

        List<AdminLogDto.LogColumnResponse> columns = getColumns(def);
        List<Map<String, Object>> rows = entities.stream()
                .map(entity -> toRow(entity, columns))
                .toList();
        Map<String, String> userNames = loadUserNames(rows, columns);

        return new AdminLogDto.LogPageResponse(
                toTableResponse(def),
                columns,
                rows,
                userNames,
                total,
                (int) Math.ceil((double) total / safePageable.getPageSize()),
                safePageable.getPageNumber(),
                safePageable.getPageSize()
        );
    }

    /**
     * 로그 일련번호로 특정 로그 행의 전체 스냅샷을 조회합니다.
     *
     * @param key    로그 테이블 키
     * @param logSno 로그 일련번호
     * @return 로그 상세 정보
     */
    public AdminLogDto.LogDetailResponse getLogDetail(String key, String logSno) {
        LogDefinition def = getDefinition(key);
        Long id;
        try {
            id = Long.parseLong(logSno);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("유효하지 않은 로그 일련번호: " + logSno);
        }
        Object entity = entityManager.find(def.entityClass(), id);
        if (entity == null) {
            throw new IllegalArgumentException("존재하지 않는 로그입니다: " + logSno);
        }

        List<AdminLogDto.LogColumnResponse> columns = getColumns(def);
        Map<String, Object> row = toRow(entity, columns);
        Map<String, String> userNames = loadUserNames(List.of(row), columns);
        return new AdminLogDto.LogDetailResponse(toTableResponse(def), columns, row, userNames);
    }

    /**
     * 로그 테이블 키로 {@link LogDefinition}을 조회합니다.
     *
     * <p>로그 테이블 종류(프로젝트, 비용, 결재 등)에 따라 등록된 정의를 반환합니다.
     * 미등록 키 입력 시 즉시 예외를 발생시켜 잘못된 테이블 접근을 방지합니다.</p>
     *
     * @param key 로그 테이블 식별 키 (예: "project", "cost")
     * @return 해당 키의 {@link LogDefinition}
     * @throws IllegalArgumentException 등록되지 않은 키인 경우
     */
    private LogDefinition getDefinition(String key) {
        LogDefinition def = definitions.get(key);
        if (def == null) {
            throw new IllegalArgumentException("조회할 수 없는 로그 테이블입니다: " + key);
        }
        return def;
    }

    /**
     * 페이지 요청값을 안전한 범위로 보정합니다.
     *
     * <p>음수 페이지번호는 0으로, 페이지크기는 1~{@code MAX_PAGE_SIZE} 범위로 클리핑합니다.
     * null {@code pageable} 입력 시 {@link NullPointerException}이 발생할 수 있습니다.</p>
     *
     * @param pageable 원본 페이지 요청
     * @return 범위 보정된 {@link Pageable}
     */
    private Pageable safePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        return PageRequest.of(page, size);
    }

    private List<AdminLogDto.LogColumnResponse> getColumns(LogDefinition def) {
        // 엔티티에 선언된 @AttributeOverride를 수집하여 BaseLogEntity 컬럼 오버라이드 적용
        Map<String, Column> overrideMap = buildAttributeOverrideMap(def.entityClass());

        List<Field> fields = new ArrayList<>();
        fields.addAll(List.of(BaseLogEntity.class.getDeclaredFields()));
        fields.addAll(List.of(def.entityClass().getDeclaredFields()));

        return fields.stream()
                .filter(field -> field.isAnnotationPresent(Column.class))
                .map(field -> toColumnResponse(field, overrideMap))
                .toList();
    }

    /**
     * 엔티티 클래스의 {@code @AttributeOverride(s)} 어노테이션을 파싱하여
     * 필드명 → 오버라이드된 {@code @Column}의 맵을 반환합니다.
     *
     * <p>BaseLogEntity 공통 컬럼이 특정 로그 테이블에서 다른 컬럼명으로
     * 매핑될 때 관리자 로그 화면에서도 올바른 컬럼 정보를 표시하기 위해 사용합니다.</p>
     *
     * @param entityClass 조회 대상 엔티티 클래스
     * @return 필드명을 키, 오버라이드 Column 어노테이션을 값으로 하는 맵
     */
    private Map<String, Column> buildAttributeOverrideMap(Class<?> entityClass) {
        Map<String, Column> map = new LinkedHashMap<>();
        AttributeOverrides multi = entityClass.getAnnotation(AttributeOverrides.class);
        if (multi != null) {
            for (AttributeOverride ao : multi.value()) {
                map.put(ao.name(), ao.column());
            }
        }
        AttributeOverride single = entityClass.getAnnotation(AttributeOverride.class);
        if (single != null) {
            map.put(single.name(), single.column());
        }
        return map;
    }

    private AdminLogDto.LogColumnResponse toColumnResponse(Field field, Map<String, Column> overrideMap) {
        // @AttributeOverride가 있으면 오버라이드된 Column 사용
        Column column = overrideMap.getOrDefault(field.getName(), field.getAnnotation(Column.class));
        String header = column.comment() == null || column.comment().isBlank()
                ? camelToLabel(field.getName())
                : column.comment();
        return new AdminLogDto.LogColumnResponse(
                field.getName(),
                column.name(),
                header,
                isUserField(field.getName()),
                "logSno".equals(field.getName())
        );
    }

    private Map<String, Object> toRow(Object entity, List<AdminLogDto.LogColumnResponse> columns) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (AdminLogDto.LogColumnResponse column : columns) {
            row.put(column.field(), readField(entity, column.field()));
        }
        return row;
    }

    /**
     * 엔티티 인스턴스에서 지정된 필드 값을 리플렉션으로 읽습니다.
     *
     * <p>현재 클래스부터 상위 클래스 계층을 순서대로 탐색합니다.</p>
     *
     * @param entity    대상 엔티티 인스턴스
     * @param fieldName 읽을 필드명 (camelCase)
     * @return 필드 값 (클래스 계층 전체에서 필드 미발견 시 {@code null} 반환)
     * @throws IllegalStateException 필드 접근 불가 시 ({@link IllegalAccessException} 래핑)
     */
    private Object readField(Object entity, String fieldName) {
        Class<?> current = entity.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(entity);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("로그 필드 값을 읽을 수 없습니다: " + fieldName, e);
            }
        }
        // 클래스 계층 전체에서 필드를 찾지 못한 경우 null 반환(기존 동작 유지).
        // 호출부가 null을 그대로 사용하므로, 매핑 누락을 진단할 수 있도록 warn 로그를 남긴다.
        log.warn("[관리자로그] 로그 필드 미발견 — null 반환: fieldName={}", fieldName);
        return null;
    }

    private Map<String, String> loadUserNames(List<Map<String, Object>> rows, List<AdminLogDto.LogColumnResponse> columns) {
        Set<String> enos = new LinkedHashSet<>();
        List<String> userFields = columns.stream()
                .filter(value -> value.userField())
                .map(value -> value.field())
                .toList();

        for (Map<String, Object> row : rows) {
            for (String field : userFields) {
                Object value = row.get(field);
                if (value instanceof String eno && !eno.isBlank()) {
                    enos.add(eno);
                }
            }
        }
        if (enos.isEmpty()) {
            return Map.of();
        }

        return userRepository.findByEnoIn(enos).stream()
                .collect(LinkedHashMap::new, (map, user) -> map.put(user.getEno(), user.getUsrNm()), (target, source) -> target.putAll(source));
    }

    private AdminLogDto.LogTableResponse toTableResponse(LogDefinition def) {
        Table table = def.entityClass().getAnnotation(Table.class);
        return new AdminLogDto.LogTableResponse(
                def.key(),
                def.title(),
                table.name(),
                def.entityClass().getSimpleName()
        );
    }

    /**
     * 필드명이 사용자 사번을 담는 필드인지 판별합니다.
     *
     * <p>판별 대상 패턴 (대소문자 무시):</p>
     * <ul>
     * <li>정확히 일치: {@code eno}, {@code mnusr}, {@code cgpreno}</li>
     * <li>접미사 일치: {@code *usid}, {@code *cgpreno}, {@code *tlr}</li>
     * </ul>
     *
     * @param fieldName 판별할 필드명 (camelCase)
     * @return 사용자 사번 필드이면 true
     */
    private boolean isUserField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.equals("eno")
                || normalized.equals("mnusr")
                || normalized.equals("cgpreno")
                || normalized.endsWith("usid")
                || normalized.endsWith("cgpreno")
                || normalized.endsWith("tlr");
    }

    private String camelToLabel(String fieldName) {
        return fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private Map<String, LogDefinition> buildDefinitions() {
        List<LogDefinition> list = List.of(
                new LogDefinition("basctm", "정보화실무협의회 신청 로그", BasctmL.class),
                new LogDefinition("bbugt", "예산 편성 로그", BbugtL.class),
                new LogDefinition("bcmmtm", "협의회 위원 로그", BcmmtmL.class),
                new LogDefinition("bcostm", "전산업무비 로그", BcostmL.class),
                new LogDefinition("bevalm", "평가 로그", BevalmL.class),
                new LogDefinition("bgdocm", "가이드 문서 로그", BgdocmL.class),
                new LogDefinition("bitemm", "사업 비목 로그", BitemmL.class),
                new LogDefinition("bperfm", "성과평가 로그", BperfmL.class),
                new LogDefinition("bplanm", "정보기술부문 계획 로그", BplanmL.class),
                new LogDefinition("bpovwm", "관점/배점 로그", BpovwmL.class),
                new LogDefinition("bpqnam", "질의응답 로그", BpqnamL.class),
                new LogDefinition("bprojm", "정보화사업 로그", BprojmL.class),
                new LogDefinition("brdocm", "요구사항 문서 로그", BrdocmL.class),
                new LogDefinition("brivgm", "검토의견 로그", BrivgmL.class),
                new LogDefinition("brsltm", "심의결과 로그", BrsltmL.class),
                new LogDefinition("bschdm", "협의회 일정 로그", BschdmL.class),
                new LogDefinition("btermm", "단말기 상세 로그", BtermmL.class),
                new LogDefinition("capplm", "전자결재 로그", CapplmL.class),
                new LogDefinition("ccodem", "공통코드 로그", CcodemL.class)
        );
        return list.stream()
                .sorted(Comparator.comparing(value -> value.key()))
                .collect(LinkedHashMap::new, (map, def) -> map.put(def.key(), def), (target, source) -> target.putAll(source));
    }

    private record LogDefinition(String key, String title, Class<?> entityClass) {}
}
