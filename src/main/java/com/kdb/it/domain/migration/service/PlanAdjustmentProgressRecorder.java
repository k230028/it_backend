package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 부문계획 조정 시트의 `사업진행` 값을 사업의 단계 상태로 기록한다 (MIG-01).
 *
 * <p>종전에는 이 값이 계획 스냅샷({@code BPLANM.REDT_CONE_INF})에만 남아 화면에서 보이지 않았다. 스냅샷 보존은 그대로 두고 원장 쪽에도 코드로 남겨
 * 진행현황 카드·목록 상태 필터가 이관된 사업의 단계를 읽을 수 있게 한다.
 *
 * <p>{@code MigrationImportService}에서 분리했다 — 오케스트레이션 서비스가 800줄 상한에 닿았고, "시트 문구를 상태코드로 옮긴다"는 판정은
 * 매핑표와 함께 한 덩어리로 움직이므로 분리 경계가 자연스럽다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanAdjustmentProgressRecorder {

    /**
     * 조정 시트 `사업진행` 열의 값 → {@code IT_PTL_STS_TC} 코드 (2026-08-23 사용자 결정).
     *
     * <p>시트가 갖는 값은 세 가지뿐이다(설계 §5.4). `진행(품의)`·`진행(계약)`은 입찰/계약 단계의 작성중·진행중에 대응하고, `취소(연기)`는 대응값이 없어
     * {@code V20260823_005}로 `00`을 신설했다. 시트에 계약 체결 전·후 구분이 없으므로 `진행(계약)`은 `75`(진행중)로 고정한다 —
     * `79`(완료)로 보면 체결 전 건까지 완료로 읽힌다.
     */
    private static final Map<String, String> STATUS_BY_PROGRESS_LABEL =
            Map.of(
                    "진행(품의)", "71",
                    "진행(계약)", "75",
                    "취소(연기)", "00");

    /**
     * 부문계획 조정으로 기록하는 BPROJA 행의 key 접두.
     *
     * <p>BPROJA는 {@code (사업관리번호, 단계 원본문서 key)} 단위이고 단계 서비스마다 자기 key를 쓴다(사업계획은 {@code BIZ-}). 조정 시트의
     * 사업진행은 사업 자신의 행에 쓰면 예산편성 요청 상태를 덮고, 부문계획 문서 행에 쓰면 부문계획 자체의 상태(11·19)와 뒤섞이므로 전용 key를 둔다.
     */
    static final String BPROJA_KEY_PREFIX = "ADJ-";

    /** 조정 시트에서 사업진행 값을 담는 스냅샷 필드명. */
    static final String PROGRESS_LABEL_FIELD = "progressLabel";

    private final BprojaSyncService bprojaSyncService;

    /**
     * 사업진행 값을 상태코드로 옮겨 BPROJA에 기록한다.
     *
     * <p>값이 비었거나 매핑에 없는 문구면 아무것도 쓰지 않는다 — 모르는 값을 임의 코드로 접으면 화면에 사실과 다른 단계가 켜진다. 원문은 계획 스냅샷에 남아 있으므로
     * 정보가 사라지지는 않는다.
     *
     * @param projectNo 사업관리번호. null/blank면 no-op
     * @param snapshotFields 어댑터가 만든 스냅샷 필드 맵({@code progressLabel} 포함). null이면 no-op
     */
    public void record(String projectNo, Map<String, String> snapshotFields) {
        if (snapshotFields == null) {
            return;
        }
        String label = snapshotFields.get(PROGRESS_LABEL_FIELD);
        if (label == null || label.isBlank()) {
            return;
        }
        String statusCode = STATUS_BY_PROGRESS_LABEL.get(label.trim());
        if (statusCode == null) {
            log.warn("사업진행 값을 상태코드로 해석하지 못해 스냅샷에만 남깁니다: project={}, value={}", projectNo, label);
            return;
        }
        bprojaSyncService.upsert(projectNo, BPROJA_KEY_PREFIX + projectNo, statusCode);
    }
}
