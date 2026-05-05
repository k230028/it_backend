package com.kdb.it.common.admin.service;

import com.kdb.it.common.admin.dto.AdminLogDto;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.log.entity.*;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLogService {

    private static final int MAX_PAGE_SIZE = 500;

    private final EntityManager entityManager;
    private final UserRepository userRepository;

    private final Map<String, LogDefinition> definitions = buildDefinitions();

    /**
     * 조회 가능한 상세 로그 테이블 목록을 반환합니다.
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
        Object entity = entityManager.find(def.entityClass(), logSno);
        if (entity == null) {
            throw new IllegalArgumentException("존재하지 않는 로그입니다: " + logSno);
        }

        List<AdminLogDto.LogColumnResponse> columns = getColumns(def);
        Map<String, Object> row = toRow(entity, columns);
        Map<String, String> userNames = loadUserNames(List.of(row), columns);
        return new AdminLogDto.LogDetailResponse(toTableResponse(def), columns, row, userNames);
    }

    private LogDefinition getDefinition(String key) {
        LogDefinition def = definitions.get(key);
        if (def == null) {
            throw new IllegalArgumentException("조회할 수 없는 로그 테이블입니다: " + key);
        }
        return def;
    }

    private Pageable safePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        return PageRequest.of(page, size);
    }

    private List<AdminLogDto.LogColumnResponse> getColumns(LogDefinition def) {
        List<Field> fields = new ArrayList<>();
        fields.addAll(List.of(BaseLogEntity.class.getDeclaredFields()));
        fields.addAll(List.of(def.entityClass().getDeclaredFields()));

        return fields.stream()
                .filter(field -> field.isAnnotationPresent(Column.class))
                .map(this::toColumnResponse)
                .toList();
    }

    private AdminLogDto.LogColumnResponse toColumnResponse(Field field) {
        Column column = field.getAnnotation(Column.class);
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
        return null;
    }

    private Map<String, String> loadUserNames(List<Map<String, Object>> rows, List<AdminLogDto.LogColumnResponse> columns) {
        Set<String> enos = new LinkedHashSet<>();
        List<String> userFields = columns.stream()
                .filter(AdminLogDto.LogColumnResponse::userField)
                .map(AdminLogDto.LogColumnResponse::field)
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
                .collect(LinkedHashMap::new, (map, user) -> map.put(user.getEno(), user.getUsrNm()), LinkedHashMap::putAll);
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

    private boolean isUserField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.equals("eno")
                || normalized.equals("mnusr")
                || normalized.equals("cgpr")
                || normalized.endsWith("usid")
                || normalized.endsWith("cgpr")
                || normalized.endsWith("tlr");
    }

    private String camelToLabel(String fieldName) {
        return fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private Map<String, LogDefinition> buildDefinitions() {
        List<LogDefinition> list = List.of(
                new LogDefinition("basctm", "정보화실무협의회 신청 로그", BasctmL.class),
                new LogDefinition("bbugt", "예산 편성 로그", BbugtL.class),
                new LogDefinition("bchklc", "체크리스트 로그", BchklcL.class),
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
                .sorted(Comparator.comparing(LogDefinition::key))
                .collect(LinkedHashMap::new, (map, def) -> map.put(def.key(), def), LinkedHashMap::putAll);
    }

    private record LogDefinition(String key, String title, Class<?> entityClass) {}
}
