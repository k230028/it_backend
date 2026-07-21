package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.exception.CustomGeneralException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * ApprovalLineDelegate 단위 테스트
 *
 * <p>TDD Red: 결재선 JSON 업데이트 위임 — 예외 재발생(롤백 보장) 동작을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalLineDelegateTest {

    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ApprovalLineDelegate approvalLineDelegate;

    @Test
    @DisplayName("doUpdate: 상세 JSON이 null이면 아무 작업 없이 정상 종료")
    void doUpdate_JSON없음_정상종료() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn(null);

        assertThatCode(() -> approvalLineDelegate.doUpdate(capplm, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("doUpdate: 상세 JSON이 빈 문자열이면 아무 작업 없이 정상 종료")
    void doUpdate_빈JSON_정상종료() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("");

        assertThatCode(() -> approvalLineDelegate.doUpdate(capplm, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("doUpdate: JSON 파싱 실패 시 cause 포함 CustomGeneralException 재발생 (트랜잭션 롤백 보장)")
    void doUpdate_JSON파싱실패_CustomGeneralException_재발생() throws Exception {
        // Arrange
        Capplm capplm = mock(Capplm.class);
        given(capplm.getApfMngNo()).willReturn("APF-202600000001");
        given(capplm.getDcdReqInf()).willReturn("{\"approvalLine\": {}}");
        given(objectMapper.readTree(anyString()))
                .willThrow(new JsonProcessingException("테스트용 JSON 파싱 오류") {});

        // Act & Assert — RED: 현재 구현은 예외를 삼키므로 이 테스트는 실패
        assertThatThrownBy(() -> approvalLineDelegate.doUpdate(capplm, List.of(), List.of()))
                .isInstanceOf(CustomGeneralException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    // ───────────────────────────────────────────────────────
    // doUpdate — approvalLine 노드 없는 JSON
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doUpdate: approvalLine 필드가 없는 JSON이면 아무 작업 없이 정상 종료")
    void doUpdate_approvalLine없음_정상종료() throws Exception {
        // Arrange: approvalLine 키 자체가 없는 유효한 JSON — 실제 ObjectMapper 사용
        ObjectMapper realMapper = new ObjectMapper();
        ApprovalLineDelegate delegateWithRealMapper = new ApprovalLineDelegate(realMapper);
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{\"title\":\"테스트\"}");
        given(capplm.getApfMngNo()).willReturn("APF-202600000002");

        // Act & Assert: approvalLine 없으면 updateDetailContent 미호출 → 정상 종료
        assertThatCode(() -> delegateWithRealMapper.doUpdate(capplm, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    // ───────────────────────────────────────────────────────
    // doUpdate — approvalLine이 배열 타입 (Object 아님)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doUpdate: approvalLine이 배열 타입이면 아무 작업 없이 정상 종료")
    void doUpdate_approvalLine배열타입_정상종료() throws Exception {
        // Arrange: approvalLine이 Object가 아닌 배열 → isObject()=false 분기 진입
        ObjectMapper realMapper = new ObjectMapper();
        ApprovalLineDelegate delegateWithRealMapper = new ApprovalLineDelegate(realMapper);
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{\"approvalLine\":[\"item1\"]}");
        given(capplm.getApfMngNo()).willReturn("APF-202600000003");

        // Act & Assert: isObject()==false → early return
        assertThatCode(() -> delegateWithRealMapper.doUpdate(capplm, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    // ───────────────────────────────────────────────────────
    // doUpdate — 결재자 일치 시 date 필드 갱신
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doUpdate: 승인된 결재자의 approvalLine 노드에 date 필드가 설정된다")
    void doUpdate_승인결재자_date필드갱신() throws Exception {
        // Arrange: 실제 ObjectMapper — approver E001, 순번 1, 승인됨
        ObjectMapper realMapper = new ObjectMapper();
        ApprovalLineDelegate delegateWithRealMapper = new ApprovalLineDelegate(realMapper);

        String json = "{\"approvalLine\":{\"step1\":{\"id\":\"E001\",\"name\":\"홍길동\"}}}";
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn(json);
        given(capplm.getApfMngNo()).willReturn("APF-202600000004");

        Cdecim approver = mock(Cdecim.class);
        given(approver.getDcrEno()).willReturn("E001");
        given(approver.getDcrSqnSno()).willReturn(1);

        Cdecim approved = mock(Cdecim.class);
        given(approved.getDcrSqnSno()).willReturn(1);

        // Act
        delegateWithRealMapper.doUpdate(capplm, List.of(approver), List.of(approved));

        // Assert: date 필드가 추가된 JSON이 updateDetailContent에 전달됨
        org.mockito.Mockito.verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                updatedJson ->
                                        updatedJson.contains("\"date\"")
                                                && updatedJson.contains("E001")));
    }

    // ───────────────────────────────────────────────────────
    // doUpdate — 위임자 없음 (결재 대상과 승인 목록 불일치)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doUpdate: allApprovers와 approvedItems 순번이 불일치하면 date 갱신이 없다")
    void doUpdate_결재불일치_갱신없음() throws Exception {
        // Arrange: E001이 allApprovers(순번 1)에 있지만 approvedItems는 순번 2
        ObjectMapper realMapper = new ObjectMapper();
        ApprovalLineDelegate delegateWithRealMapper = new ApprovalLineDelegate(realMapper);

        String json = "{\"approvalLine\":{\"step1\":{\"id\":\"E001\",\"name\":\"홍길동\"}}}";
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn(json);
        given(capplm.getApfMngNo()).willReturn("APF-202600000005");

        Cdecim approver = mock(Cdecim.class);
        given(approver.getDcrEno()).willReturn("E001");
        given(approver.getDcrSqnSno()).willReturn(1);

        Cdecim notApproved = mock(Cdecim.class);
        given(notApproved.getDcrSqnSno()).willReturn(2);

        // Act
        delegateWithRealMapper.doUpdate(capplm, List.of(approver), List.of(notApproved));

        // Assert: updated=false → updateDetailContent 미호출
        org.mockito.Mockito.verify(capplm, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("applyRecallInfo: 빈 상세 JSON에는 회수 정보를 새로 기록한다")
    void applyRecallInfo_빈JSON_회수정보기록() {
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("");

        delegate.applyRecallInfo(capplm, "E001", "재작성 필요");

        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json ->
                                        json.contains("\"recallerEno\":\"E001\"")
                                                && json.contains("\"recallOpnn\":\"재작성 필요\"")));
    }

    @Test
    @DisplayName("applyRecallInfo: 잘못된 JSON이면 IllegalStateException을 던진다")
    void applyRecallInfo_잘못된JSON_예외발생() {
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{");

        assertThatThrownBy(() -> delegate.applyRecallInfo(capplm, "E001", "회수"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("회수 정보 JSON 갱신 실패");
    }

    @Test
    @DisplayName("doUpdate: 기안자 노드는 결재 횟수 계산에서 제외하고 승인자만 갱신한다")
    void doUpdate_기안자제외_승인자갱신() {
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn(
                        "{\"approvalLine\":{\"drafter\":{\"id\":\"E001\"},\"step1\":{\"id\":\"E001\"},\"caption\":\"text\"}}");
        Cdecim approver = mock(Cdecim.class);
        given(approver.getDcrEno()).willReturn("E001");
        given(approver.getDcrSqnSno()).willReturn(1);

        delegate.doUpdate(capplm, List.of(approver), List.of(approver));

        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json ->
                                        json.contains("\"step1\":{\"id\":\"E001\",\"date\"")
                                                && !json.contains(
                                                        "\"drafter\":{\"id\":\"E001\",\"date\"")));
    }
}
