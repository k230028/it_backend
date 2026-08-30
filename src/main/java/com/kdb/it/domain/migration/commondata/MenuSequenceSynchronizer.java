package com.kdb.it.domain.migration.commondata;

import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 메뉴 이관 후 {@code SQ_TPRMPP_CMENUM_1}을 파일 최대 메뉴 번호 이상으로 전진시킵니다.
 *
 * <p>ALTER SEQUENCE는 DDL이라 implicit commit으로 이관 트랜잭션의 원자성을 깨뜨리므로 쓰지 않고, NEXTVAL을 반복 소비해
 * 전진시킵니다(ITPAPP 계정은 시퀀스 SELECT 권한만으로 충분). 반드시 커밋 트랜잭션 밖에서 호출합니다. 전진 실패는 이관 자체를 되돌릴 이유가 아니므로 예외 대신
 * 경고 문자열을 반환합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MenuSequenceSynchronizer {

    private static final Pattern MNU_ID_PATTERN = Pattern.compile("^MNU(\\d{7})$");
    private static final String NEXTVAL_SQL = "SELECT SQ_TPRMPP_CMENUM_1.NEXTVAL FROM DUAL";

    /** 폭주 방지 상한. 메뉴는 수십 건 규모라 실제로는 수십 회면 끝난다. */
    private static final int MAX_STEPS = 10_000;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 파일 메뉴ID의 최대 번호 이상이 될 때까지 시퀀스를 소비합니다.
     *
     * @param menus 업로드된 메뉴 행 (MNU\d{7} 형식이 아닌 ID는 무시)
     * @return 전진 실패·미완료 시 응답에 덧붙일 경고 문구
     */
    public Optional<String> advanceTo(List<CommonDataMigrationDto.MenuRow> menus) {
        long maxSeq =
                menus.stream()
                        .map(row -> MNU_ID_PATTERN.matcher(row.mnuId()))
                        .filter(Matcher::matches)
                        .mapToLong(m -> Long.parseLong(m.group(1)))
                        .max()
                        .orElse(0);
        if (maxSeq == 0) {
            return Optional.empty();
        }
        try {
            for (int step = 0; step < MAX_STEPS; step++) {
                Long current = jdbcTemplate.queryForObject(NEXTVAL_SQL, Long.class);
                if (current != null && current >= maxSeq) {
                    return Optional.empty();
                }
            }
            return Optional.of("메뉴 시퀀스를 " + maxSeq + " 이상으로 전진시키지 못했습니다. DBA 확인이 필요합니다.");
        } catch (RuntimeException e) {
            log.warn("메뉴 시퀀스 동기화에 실패했습니다. 이관 반영 자체는 완료된 상태입니다.", e);
            return Optional.of("메뉴 시퀀스 동기화에 실패했습니다. 신규 메뉴 생성 전 DBA 확인이 필요합니다 (ORA-00001 위험).");
        }
    }
}
