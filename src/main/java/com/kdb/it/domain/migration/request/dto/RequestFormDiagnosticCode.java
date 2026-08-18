package com.kdb.it.domain.migration.request.dto;

import com.kdb.it.domain.migration.dto.MigrationDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 편성요청서 반입 진단 코드입니다.
 *
 * <p>심각도를 코드에 붙여 두어 검증기가 코드만 고르면 심각도가 따라오게 합니다. BLOCKER가 하나라도 남은 파일은 반영하지 않고, WARNING만 있는 파일은
 * 반영합니다. 심각도를 호출부에서 정하게 하면 같은 코드가 파일마다 다른 심각도로 나가 사용자가 기준을 잡을 수 없습니다.
 */
@Schema(name = "RequestFormDiagnosticCode", description = "편성요청서 반입 진단 코드")
public enum RequestFormDiagnosticCode {
    /** POI가 워크북을 열지 못함 (손상·암호·엑셀 아님) */
    FILE_UNREADABLE(MigrationDto.Severity.BLOCKER),
    /**
     * 인식 가능한 시트가 하나도 없음 — 반입 대상이 아님.
     *
     * <p>BLOCKER가 아닙니다. 부점 폴더 아래에 팀·사업 폴더가 더 있는 구조에서는 편성요청서가 아닌 엑셀이 함께 올라오는 것이 정상 케이스이고, 그것은
     * 관리자가 고칠 수 있는 결함이 아닙니다. 이 코드를 받은 파일은 {@code FileStatus.SKIPPED}로 남습니다.
     */
    SHEET_NOT_FOUND(MigrationDto.Severity.WARNING),
    /** 라벨·헤더 앵커 실패 (양식이 과도하게 개조됨) */
    ANCHOR_NOT_FOUND(MigrationDto.Severity.BLOCKER),
    /** 폴더명·주관부서/팀이 조직에 없음 */
    ORG_UNRESOLVED(MigrationDto.Severity.BLOCKER),
    /** 조직 후보가 둘 이상 */
    ORG_AMBIGUOUS(MigrationDto.Severity.BLOCKER),
    /** 값이 있는데 사용자 해석 실패 (공란은 진단 대상이 아님) */
    USER_UNRESOLVED(MigrationDto.Severity.BLOCKER),
    /** 동명이인 */
    USER_AMBIGUOUS(MigrationDto.Severity.BLOCKER),
    /** 비목·통화·전결권·추진가능성 미매칭 */
    CODE_UNRESOLVED(MigrationDto.Severity.BLOCKER),
    /** 코드 후보가 둘 이상 (전산제비, 외주용역 등) */
    CODE_AMBIGUOUS(MigrationDto.Severity.BLOCKER),
    /** 사업명·품목 금액 등 필수값 공백 */
    REQUIRED_MISSING(MigrationDto.Severity.BLOCKER),
    /** 자연키로 기존 행이 이미 존재 (재업로드 거부) */
    DUPLICATE_EXISTS(MigrationDto.Severity.BLOCKER),
    /** 물리 컬럼 길이 초과 */
    LENGTH_EXCEEDED(MigrationDto.Severity.BLOCKER),
    /**
     * 코드를 기본값으로 정했고 대안이 있음 — 확인 요청.
     *
     * <p>{@link #CODE_AMBIGUOUS}와 구분해야 합니다. 저쪽은 <b>아무 코드도 못 정한</b> 상태라 반영을 막아야 하고, 이쪽은 <b>합리적인 기본값을
     * 정했지만</b> 다른 선택지가 있는 상태입니다. 둘을 같은 BLOCKER로 묶으면 기본값을 제시하는 의미가 사라져, 개발비·기타무형자산 품목이 있는 파일이 사람이 매번
     * 같은 값을 다시 고르기 전까지 전부 차단됩니다.
     */
    CODE_DEFAULTED(MigrationDto.Severity.WARNING),
    /** 시트 ③ 단위를 휴리스틱으로 추정함 — 확인 요청 */
    UNIT_UNCERTAIN(MigrationDto.Severity.WARNING),
    /** 1-1 요약 대 1-2 합계, 월간×주기 대 연간, 소계·총계 불일치 */
    AMOUNT_MISMATCH(MigrationDto.Severity.WARNING),
    /** 사업구분·편성기준·보고상태 항목이 공란 */
    OPTIONAL_MISSING(MigrationDto.Severity.WARNING),
    /** 실무자(부)처럼 담을 컬럼이 없어 미적재한 값 */
    SUBSTITUTE_DROPPED(MigrationDto.Severity.WARNING),
    /** `25/06` 같은 날짜 표기 파싱 실패 → null */
    DATE_UNPARSEABLE(MigrationDto.Severity.WARNING);

    private final MigrationDto.Severity severity;

    RequestFormDiagnosticCode(MigrationDto.Severity severity) {
        this.severity = severity;
    }

    /**
     * 이 코드의 심각도를 반환합니다.
     *
     * @return BLOCKER 또는 WARNING
     */
    public MigrationDto.Severity severity() {
        return severity;
    }

    /**
     * 반영을 막는 코드인지 판정합니다.
     *
     * @return BLOCKER이면 true
     */
    public boolean blocks() {
        return severity == MigrationDto.Severity.BLOCKER;
    }
}
