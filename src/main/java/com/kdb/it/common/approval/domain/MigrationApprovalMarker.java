package com.kdb.it.common.approval.domain;

/**
 * 수기 엑셀 이관으로 만들어진 신청서 기록을 식별하는 표식입니다.
 *
 * <p>이관 받이는 결재선({@code TPRMPP_CDECIM})을 만들지 않고 신청서 본문({@code APF_DTL_CONE})도 비어 있어, 일반 신청서와 같은 화면
 * 흐름을 태우면 빈 문서가 됩니다. 그 구분을 등록자결재요청내용({@code RGPR_DCD_REQ_CONE})에 남기는 고정 문구로 합니다.
 *
 * <p>이 상수는 이관을 만드는 쪽({@code MigrationApprovalStamper})과 읽는 쪽({@code ApplicationDto})이 함께 쓰므로 공통
 * 패키지에 둡니다. {@code common}이 {@code domain.migration}을 참조하는 역방향 의존을 막기 위한 배치입니다. 문구를 바꾸면 이미 저장된 기존
 * 이관 데이터가 일반 신청서로 보이게 되므로 변경하지 않습니다.
 */
public final class MigrationApprovalMarker {

    /** 이관으로 생성된 신청서 기록임을 등록자결재요청내용에 남기는 고정 문구입니다. */
    public static final String NOTE = "수기 엑셀 이관으로 생성된 결재완료 기록입니다. 실제 결재선을 거치지 않았습니다.";

    private MigrationApprovalMarker() {
        throw new UnsupportedOperationException("상수 컨테이너 — 인스턴스화 금지");
    }

    /**
     * 등록자결재요청내용이 이관 표식인지 판정합니다.
     *
     * @param rgprDcdReqCone 등록자결재요청내용. null이면 false
     * @return 이관으로 생성된 신청서 기록이면 true
     */
    public static boolean isMigrated(String rgprDcdReqCone) {
        return NOTE.equals(rgprDcdReqCone);
    }
}
