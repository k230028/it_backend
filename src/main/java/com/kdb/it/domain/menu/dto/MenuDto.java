package com.kdb.it.domain.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 메뉴 관리 API의 요청/응답 DTO 모음 (네임스페이스 클래스). */
public class MenuDto {

    /** 사이드바·Breadcrumb 공용 트리 노드. 아이콘은 메뉴 행({@code imkNm})이 단일 출처이고, 배지만 프론트 규약 맵 소관이다. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "MenuNode")
    public static class Node {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String mnuId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private String hrkMnuId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String mnuNm;

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"GRP", "LNK", "PGE", "BRD"})
        private String mnuTpC; // GRP / LNK / PGE

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private String srePth;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer mnuSotSqnSno;

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"Y", "N"})
        private String hidYn;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer mnuDep;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String whlMnuPth;

        /** 사이드바 아이콘 클래스(예: {@code pi pi-home}). 미지정이면 null. */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private String imkNm;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private List<Node> children;

        /** 노출 권한ID 목록. 빈 목록=전체 공개. 관리 트리는 편집 폼 복원용, 사용자 트리는 왕관 아이콘 표시 판정용. */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private List<String> athIds;
    }

    /** 단건 생성/수정 요청. mnuId는 서버가 채번하므로 받지 않는다. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "MenuUpsertRequest")
    public static class UpsertRequest {
        @NotBlank
        @Schema(description = "메뉴명")
        private String mnuNm;

        @NotBlank
        @Schema(description = "메뉴유형코드 GRP/LNK/PGE")
        private String mnuTpC;

        @Schema(description = "상위메뉴ID(루트면 null)")
        private String hrkMnuId;

        @Schema(description = "화면경로(PGE 필수)")
        private String srePth;

        @Schema(description = "숨김여부 Y/N")
        private String hidYn;

        @Schema(description = "아이콘 클래스(예: pi pi-home). 비우면 미지정", example = "pi pi-home")
        private String imkNm;

        @Schema(description = "노출 권한ID 목록(비우면 전체 공개)")
        private List<String> athIds;
    }

    /** 같은 부모 내 순서 일괄 변경. mnuId 순서대로 10,20,30... 부여. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "MenuReorderRequest")
    public static class ReorderRequest {
        @NotNull
        @Schema(description = "정렬된 mnuId 목록")
        private List<String> orderedMnuIds;
    }

    /**
     * 부모 이동 요청.
     *
     * <p>검증 규칙: {@code newHrkMnuId}가 null이면 루트(최상위)로 이동한다. 루트 이동이 합법이므로 필드 단위 필수 검증(@NotNull)을 두지
     * 않으며, 빈 본문({})의 요청도 루트 이동으로 해석된다. 잘못된 대상 메뉴·순환 계층은 서비스 계층에서 검증한다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "MenuMoveRequest", description = "newHrkMnuId가 null이면 루트(최상위)로 이동")
    public static class MoveRequest {
        @Schema(description = "새 상위메뉴ID(루트로 이동하면 null)")
        private String newHrkMnuId;
    }

    /** 라우트 카탈로그 행. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "RouteCatalogItem")
    public static class Route {
        @NotBlank private String srePth;
        @NotBlank private String sreMnuNm;
        private String useYn;
        private String rmk;
    }
}
