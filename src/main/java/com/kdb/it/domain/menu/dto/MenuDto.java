package com.kdb.it.domain.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 메뉴 관리 API의 요청/응답 DTO 모음 (네임스페이스 클래스). */
public class MenuDto {

    /** 사이드바·Breadcrumb 공용 트리 노드. 아이콘/배지는 프론트 규약 맵 소관이라 미포함. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(name = "MenuNode")
    public static class Node {
        private String mnuId;
        private String hrkMnuId;
        private String mnuNm;
        private String mnuTpC;       // LNK / GRP / DYN
        private String srePth;
        private Integer mnuSotSqnSno;
        private String hidYn;
        private Integer mnuDep;
        private String whlMnuPth;
        private List<Node> children;
        /** 노출 권한ID 목록(관리 트리에서만 채움). 빈 목록=전체 공개. 사용자 트리에서는 null. */
        private List<String> athIds;
    }

    /** 단건 생성/수정 요청. mnuId는 서버가 채번하므로 받지 않는다. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(name = "MenuUpsertRequest")
    public static class UpsertRequest {
        @NotBlank @Schema(description = "메뉴명") private String mnuNm;
        @NotBlank @Schema(description = "메뉴유형코드 LNK/GRP/DYN/HED") private String mnuTpC;
        @Schema(description = "상위메뉴ID(루트면 null)") private String hrkMnuId;
        @Schema(description = "화면경로(LNK 필수)") private String srePth;
        @Schema(description = "숨김여부 Y/N") private String hidYn;
        @Schema(description = "노출 권한ID 목록(비우면 전체 공개)") private List<String> athIds;
    }

    /** 같은 부모 내 순서 일괄 변경. mnuId 순서대로 10,20,30... 부여. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(name = "MenuReorderRequest")
    public static class ReorderRequest {
        @NotNull @Schema(description = "정렬된 mnuId 목록") private List<String> orderedMnuIds;
    }

    /** 부모 이동. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(name = "MenuMoveRequest")
    public static class MoveRequest {
        @Schema(description = "새 상위메뉴ID(루트로 이동하면 null)") private String newHrkMnuId;
    }

    /** 라우트 카탈로그 행. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(name = "RouteCatalogItem")
    public static class Route {
        @NotBlank private String srePth;
        @NotBlank private String sreMnuNm;
        private String sysHrkMnuId;
        private String useYn;
        private String rmk;
    }
}
