package com.kdb.it.common.approval.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.approval.entity.Capplm;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신청서에서 메일 렌더링 입력을 만드는 규칙을 검증한다. */
class ApprovalMailContextFactoryTest {

    private static Capplm application() {
        return Capplm.builder()
                .apfMngNo("APF-2026-0001")
                .dcdReqTtl("전산예산 신청서")
                .dcdReqDtm(LocalDate.of(2026, 8, 18))
                .dcdReqInf("{\"projects\":[],\"costs\":[]}")
                .build();
    }

    @Test
    @DisplayName("상세 URL은 프론트 URL과 신청관리번호로 만든다")
    void create_buildsDetailUrl() {
        ApprovalMailContext context =
                ApprovalMailContextFactory.create(
                        application(), "홍길동", "IT기획부", "https://it.kdb.co.kr");

        assertThat(context.detailUrl()).isEqualTo("https://it.kdb.co.kr/approval/APF-2026-0001");
    }

    @Test
    @DisplayName("프론트 URL 끝의 슬래시는 중복되지 않는다")
    void create_normalizesTrailingSlash() {
        ApprovalMailContext context =
                ApprovalMailContextFactory.create(
                        application(), "홍길동", "IT기획부", "https://it.kdb.co.kr///");

        assertThat(context.detailUrl()).isEqualTo("https://it.kdb.co.kr/approval/APF-2026-0001");
    }

    @Test
    @DisplayName("프론트 URL이 null이면 빈 문자열로 대체해 상대 경로만 남긴다")
    void create_nullFrontendUrl_fallsBackToRelativePath() {
        ApprovalMailContext context =
                ApprovalMailContextFactory.create(application(), "홍길동", "IT기획부", null);

        assertThat(context.detailUrl()).isEqualTo("/approval/APF-2026-0001");
    }

    @Test
    @DisplayName("신청서 값이 그대로 옮겨진다")
    void create_copiesApplicationFields() {
        ApprovalMailContext context =
                ApprovalMailContextFactory.create(
                        application(), "홍길동", "IT기획부", "https://it.kdb.co.kr");

        assertThat(context.apfMngNo()).isEqualTo("APF-2026-0001");
        assertThat(context.title()).isEqualTo("전산예산 신청서");
        assertThat(context.requestedDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(context.requesterName()).isEqualTo("홍길동");
        assertThat(context.deptName()).isEqualTo("IT기획부");
        assertThat(context.detailJson()).isEqualTo("{\"projects\":[],\"costs\":[]}");
    }
}
