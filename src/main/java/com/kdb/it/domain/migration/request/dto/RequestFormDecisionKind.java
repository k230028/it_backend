package com.kdb.it.domain.migration.request.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 진단을 해소하려면 사람이 무엇을 입력해야 하는지입니다.
 *
 * <p>화면이 진단 종류를 보고 입력 위젯을 추측하지 않도록 <b>서버가 지정합니다</b>. 서버는 여기서 지정한 종류에 대해 같은 이름의 보정값을 실제로 읽어 반영한다는 것을
 * 함께 약속합니다 — 화면에 입력칸만 있고 서버가 그 값을 무시하면 사용자는 고쳤다고 믿은 채 같은 진단을 다시 보게 됩니다.
 */
@Schema(name = "RequestFormDecisionKind", description = "진단 해소에 필요한 입력 종류")
public enum RequestFormDecisionKind {
    /** 사람이 고를 것이 없습니다. 원본 파일을 고쳐 다시 올려야 합니다 */
    NONE,
    /** 후보 목록에서 하나를 고릅니다. 고른 값이 그대로 저장값입니다 */
    SELECT,
    /** 자유 입력 */
    TEXT,
    /** 연월일 8자리(`YYYYMMDD`) 입력 */
    DATE,
    /** 시트 ③ 금액 단위. 행이 아니라 <b>파일 단위</b> 값이라 셀 보정이 아니라 파일 항목으로 적용합니다 */
    AMOUNT_UNIT
}
