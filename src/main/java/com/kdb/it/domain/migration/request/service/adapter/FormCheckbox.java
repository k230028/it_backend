package com.kdb.it.domain.migration.request.service.adapter;

/**
 * 시트 위에 놓인 양식 컨트롤(Form Control) 체크박스 하나입니다.
 *
 * <p>체크박스는 셀 값이 아니라 시트 위에 떠 있는 도형이라 일반 셀 읽기로는 보이지 않습니다. 위치는 도형이 걸린 좌상단 셀 좌표이고, 실제 그림은 그 셀 안쪽 오프셋만큼
 * 밀려 있으므로 열 좌표는 <b>라벨 열 이상</b>이라는 정도로만 신뢰합니다.
 *
 * @param rowIndex 0-based 행 번호
 * @param colIndex 0-based 열 번호
 * @param caption 체크박스 옆 문구 (`여신`, `비중복(N)` 등). 없으면 빈 문자열
 * @param checked 체크되어 있으면 true
 */
public record FormCheckbox(int rowIndex, int colIndex, String caption, boolean checked) {}
