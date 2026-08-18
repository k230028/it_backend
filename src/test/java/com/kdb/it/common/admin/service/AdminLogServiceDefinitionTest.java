package com.kdb.it.common.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.admin.dto.AdminLogDto;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 로그 테이블 정의 목록에 번역 로그가 등록되어 있는지 고정합니다. */
class AdminLogServiceDefinitionTest {

    @Test
    void 번역로그가_공용_로그정의에_등록되어_있다() {
        AdminLogService service = new AdminLogService(null, null);

        List<AdminLogDto.LogTableResponse> tables = service.getTables();

        List<AdminLogDto.LogTableResponse> matches =
                tables.stream().filter(table -> table.key().equals("clangm")).toList();
        assertThat(matches).hasSize(1);

        AdminLogDto.LogTableResponse clangm = matches.get(0);
        assertThat(clangm.title()).isEqualTo("다국어 번역 로그");
        assertThat(clangm.tableName()).isEqualTo("TPRMPP_CLANGL");
        assertThat(clangm.entityName()).isEqualTo("ClangmL");
    }
}
