package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.exception.CustomGeneralException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    @DisplayName("doUpdate: 추가 결재자 배열 항목에도 승인일을 기록한다")
    void doUpdate_추가결재자배열_date필드갱신() {
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn(
                        "{\"approvalLine\":{\"teamLead\":{\"id\":\"E001\"},\"additionalApprovers\":[{\"id\":\"E002\"}]}}");
        Cdecim first = mock(Cdecim.class);
        given(first.getDcrEno()).willReturn("E001");
        given(first.getDcrSqnSno()).willReturn(1);
        Cdecim additional = mock(Cdecim.class);
        given(additional.getDcrEno()).willReturn("E002");
        given(additional.getDcrSqnSno()).willReturn(2);

        delegate.doUpdate(capplm, List.of(first, additional), List.of(additional));

        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json ->
                                        json.contains("\"id\":\"E002\"")
                                                && json.contains("\"date\"")));
    }

    @Test
    @DisplayName("추가 결재자를 JSON 결재선에 추가하고 지정 항목을 삭제한다")
    void 추가결재자_JSON추가삭제() throws Exception {
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn("{\"approvalLine\":{\"teamLead\":{\"id\":\"E001\"}}}");

        delegate.addApproverToDetail(capplm, "E002", "김결재", "대리");

        org.mockito.ArgumentCaptor<String> jsonCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(capplm).updateDetailContent(jsonCaptor.capture());
        String updatedJson = jsonCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(updatedJson)
                .contains("\"id\":\"E002\"")
                .contains("\"name\":\"김결재\"");

        given(capplm.getDcdReqInf()).willReturn(updatedJson);
        delegate.removeApproverFromDetail(capplm, 0);
        verify(capplm, org.mockito.Mockito.times(2))
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    // ───────────────────────────────────────────────────────
    // doUpdate — 저장된 order 배열 기반 갱신 (applyDateInStoredOrder)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doUpdate: order 배열이 있으면 저장된 순서대로 승인일을 반영한다")
    void doUpdate_order배열_저장된순서로갱신() {
        // Arrange: order 배열에 미존재 사번(GHOST)과 중복 사번(E001)을 포함해 skip 분기까지 검증
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        String json =
                "{\"approvalLine\":{\"drafter\":{\"id\":\"E009\"},"
                        + "\"order\":[\"E002\",\"GHOST\",\"E001\",\"E001\"],"
                        + "\"teamLead\":{\"id\":\"E001\"},"
                        + "\"deptHead\":{\"id\":\"E002\"},"
                        + "\"additionalApprovers\":[{\"id\":\"E003\"},\"메모\"],"
                        + "\"caption\":\"텍스트\"}}";
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn(json);

        Cdecim first = mock(Cdecim.class);
        given(first.getDcrEno()).willReturn("E002");
        given(first.getDcrSqnSno()).willReturn(1);
        Cdecim second = mock(Cdecim.class);
        given(second.getDcrEno()).willReturn("E001");
        given(second.getDcrSqnSno()).willReturn(2);
        Cdecim third = mock(Cdecim.class);
        given(third.getDcrEno()).willReturn("E003");
        given(third.getDcrSqnSno()).willReturn(3);
        Cdecim approved = mock(Cdecim.class);
        given(approved.getDcrSqnSno()).willReturn(1);

        // Act
        delegate.doUpdate(capplm, List.of(first, second, third), List.of(approved));

        // Assert: 승인된 E002(deptHead)만 date가 기록되고 E001(teamLead)은 유지
        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                updatedJson ->
                                        updatedJson.contains(
                                                        "\"deptHead\":{\"id\":\"E002\",\"date\"")
                                                && !updatedJson.contains(
                                                        "\"teamLead\":{\"id\":\"E001\",\"date\"")));
    }

    @Test
    @DisplayName("doUpdate: 동일 사번이 결재선에 두 번 등장하면 승인된 occurrence만 갱신한다")
    void doUpdate_동일사번중복_승인된occurrence만갱신() {
        // Arrange: E001이 두 번 등장, 두 번째 순번(occurrence 2)만 승인됨
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn(
                        "{\"approvalLine\":{\"step1\":{\"id\":\"E001\"},\"step2\":{\"id\":\"E001\"}}}");
        Cdecim occurrence1 = mock(Cdecim.class);
        given(occurrence1.getDcrEno()).willReturn("E001");
        given(occurrence1.getDcrSqnSno()).willReturn(1);
        Cdecim occurrence2 = mock(Cdecim.class);
        given(occurrence2.getDcrEno()).willReturn("E001");
        given(occurrence2.getDcrSqnSno()).willReturn(2);

        // Act
        delegate.doUpdate(capplm, List.of(occurrence1, occurrence2), List.of(occurrence2));

        // Assert: step1은 미갱신, step2만 date 기록
        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json ->
                                        !json.contains("\"step1\":{\"id\":\"E001\",\"date\"")
                                                && json.contains(
                                                        "\"step2\":{\"id\":\"E001\",\"date\"")));
    }

    @Test
    @DisplayName("doUpdate: 배열 내 id 없는 항목과 스칼라 항목은 무시하고 유효 항목만 갱신한다")
    void doUpdate_배열내_id없는항목_무시() {
        // Arrange: additionalApprovers에 스칼라·id 없는 객체·유효 객체 혼재
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn(
                        "{\"approvalLine\":{\"additionalApprovers\":[\"메모\",{\"name\":\"이름만\"},{\"id\":\"E001\"}]}}");
        Cdecim approver = mock(Cdecim.class);
        given(approver.getDcrEno()).willReturn("E001");
        given(approver.getDcrSqnSno()).willReturn(1);

        // Act
        delegate.doUpdate(capplm, List.of(approver), List.of(approver));

        // Assert: 유효한 E001 항목에만 date 기록
        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json -> json.contains("\"id\":\"E001\",\"date\"")));
    }

    // ───────────────────────────────────────────────────────
    // applyRecallInfo — 분기 보강
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("applyRecallInfo: 상세 JSON이 null이면 새 객체에 회수 정보를 기록한다")
    void applyRecallInfo_null_JSON_회수정보기록() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn(null);

        // Act
        delegate.applyRecallInfo(capplm, "E001", "회수 사유");

        // Assert
        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json ->
                                        json.contains("\"recallerEno\":\"E001\"")
                                                && json.contains("\"recallOpnn\":\"회수 사유\"")));
    }

    @Test
    @DisplayName("applyRecallInfo: 기존 JSON 필드를 유지하며 recallInfo를 추가한다")
    void applyRecallInfo_기존JSON유지_회수정보추가() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{\"title\":\"기존제목\"}");

        // Act
        delegate.applyRecallInfo(capplm, "E002", "재검토");

        // Assert: 기존 필드 유지 + recallInfo 추가
        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json ->
                                        json.contains("\"title\":\"기존제목\"")
                                                && json.contains("\"recallInfo\"")));
    }

    @Test
    @DisplayName("applyRecallInfo: 저장된 JSON이 배열이면 IllegalStateException을 던진다")
    void applyRecallInfo_배열JSON_예외발생() {
        // Arrange: ObjectNode 캐스팅 실패 → ClassCastException 분기
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("[1,2]");

        // Act & Assert
        assertThatThrownBy(() -> delegate.applyRecallInfo(capplm, "E001", "회수"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("회수 정보 JSON 갱신 실패");
    }

    // ───────────────────────────────────────────────────────
    // addApproverToDetail — 분기 보강
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("addApproverToDetail: 상세 JSON이 null 또는 공백이면 아무 작업도 하지 않는다")
    void addApproverToDetail_JSON없음_미수행() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm nullJson = mock(Capplm.class);
        given(nullJson.getDcdReqInf()).willReturn(null);
        Capplm blankJson = mock(Capplm.class);
        given(blankJson.getDcdReqInf()).willReturn("   ");

        // Act
        delegate.addApproverToDetail(nullJson, "E002", "이름", "직급");
        delegate.addApproverToDetail(blankJson, "E002", "이름", "직급");

        // Assert
        verify(nullJson, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
        verify(blankJson, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("addApproverToDetail: approvalLine이 객체가 아니면 아무 작업도 하지 않는다")
    void addApproverToDetail_결재선객체아님_미수행() {
        // Arrange: approvalLine이 배열 → ObjectNode 아님
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{\"approvalLine\":[]}");

        // Act
        delegate.addApproverToDetail(capplm, "E002", "이름", "직급");

        // Assert
        verify(capplm, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("addApproverToDetail: 이름·직급이 null이면 빈 문자열로 기록한다")
    void addApproverToDetail_이름직급null_빈문자열기록() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn("{\"approvalLine\":{\"teamLead\":{\"id\":\"E001\"}}}");

        // Act
        delegate.addApproverToDetail(capplm, "E002", null, null);

        // Assert: null 방어 분기 → "" 기록
        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json ->
                                        json.contains("\"name\":\"\"")
                                                && json.contains("\"rank\":\"\"")
                                                && json.contains("\"id\":\"E002\"")));
    }

    @Test
    @DisplayName("addApproverToDetail: JSON 파싱 실패 시 CustomGeneralException을 던진다")
    void addApproverToDetail_JSON파싱실패_예외발생() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{잘못된JSON");
        given(capplm.getApfMngNo()).willReturn("APF-202600000010");

        // Act & Assert
        assertThatThrownBy(() -> delegate.addApproverToDetail(capplm, "E002", "이름", "직급"))
                .isInstanceOf(CustomGeneralException.class);
    }

    // ───────────────────────────────────────────────────────
    // removeApproverFromDetail — 분기 보강
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("removeApproverFromDetail: 상세 JSON이 null 또는 공백이면 아무 작업도 하지 않는다")
    void removeApproverFromDetail_JSON없음_미수행() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm nullJson = mock(Capplm.class);
        given(nullJson.getDcdReqInf()).willReturn(null);
        Capplm blankJson = mock(Capplm.class);
        given(blankJson.getDcdReqInf()).willReturn(" ");

        // Act
        delegate.removeApproverFromDetail(nullJson, 0);
        delegate.removeApproverFromDetail(blankJson, 0);

        // Assert
        verify(nullJson, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
        verify(blankJson, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("removeApproverFromDetail: approvalLine이 객체가 아니면 아무 작업도 하지 않는다")
    void removeApproverFromDetail_결재선객체아님_미수행() {
        // Arrange: approvalLine 키 자체가 없는 JSON → MissingNode
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{\"title\":\"제목\"}");

        // Act
        delegate.removeApproverFromDetail(capplm, 0);

        // Assert
        verify(capplm, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("removeApproverFromDetail: additionalApprovers 배열이 없으면 아무 작업도 하지 않는다")
    void removeApproverFromDetail_추가결재자배열없음_미수행() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn("{\"approvalLine\":{\"teamLead\":{\"id\":\"E001\"}}}");

        // Act
        delegate.removeApproverFromDetail(capplm, 0);

        // Assert
        verify(capplm, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("removeApproverFromDetail: 인덱스가 음수이거나 범위를 벗어나면 아무 작업도 하지 않는다")
    void removeApproverFromDetail_인덱스범위밖_미수행() {
        // Arrange: 배열 크기 1 → -1과 5 모두 범위 밖
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn("{\"approvalLine\":{\"additionalApprovers\":[{\"id\":\"E001\"}]}}");

        // Act
        delegate.removeApproverFromDetail(capplm, -1);
        delegate.removeApproverFromDetail(capplm, 5);

        // Assert
        verify(capplm, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("removeApproverFromDetail: JSON 파싱 실패 시 CustomGeneralException을 던진다")
    void removeApproverFromDetail_JSON파싱실패_예외발생() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{잘못된JSON");
        given(capplm.getApfMngNo()).willReturn("APF-202600000011");

        // Act & Assert
        assertThatThrownBy(() -> delegate.removeApproverFromDetail(capplm, 0))
                .isInstanceOf(CustomGeneralException.class);
    }

    // ───────────────────────────────────────────────────────
    // replacePendingApproversInDetail
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("replacePendingApproversInDetail: 저장된 order가 앞당긴 완료 추가 결재자 노드를 보존한다")
    void replacePendingApproversInDetail_재정렬된완료추가결재자_보존() throws Exception {
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        AtomicReference<String> detail =
                new AtomicReference<>(
                        "{\"approvalLine\":{\"teamLead\":{\"id\":\"E001\",\"name\":\"기존팀장\",\"rank\":\"부장\"},"
                                + "\"departmentHead\":{\"id\":\"E002\",\"name\":\"기존부서장\",\"rank\":\"이사\"},"
                                + "\"additionalApprovers\":[{\"id\":\"E003\",\"name\":\"완료추가결재자\",\"rank\":\"차장\",\"date\":\"2026-09-01\"}],"
                                + "\"order\":[\"E003\",\"E001\",\"E002\"]}}");
        given(capplm.getDcdReqInf()).willAnswer(invocation -> detail.get());
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            detail.set(invocation.getArgument(0));
                            return null;
                        })
                .when(capplm)
                .updateDetailContent(anyString());

        Cdecim completedAdditional = approver("E003", "2");
        Cdecim pendingFirst = approver("E100", "1");
        Cdecim pendingSecond = approver("E101", "1");
        CuserI firstUser = CuserI.builder().eno("E100").usrNm("새팀장").ptCNm("부장").build();
        CuserI secondUser = CuserI.builder().eno("E101").usrNm("새부서장").ptCNm("이사").build();
        List<Cdecim> finalOrder = List.of(completedAdditional, pendingFirst, pendingSecond);

        delegate.replacePendingApproversInDetail(
                capplm, finalOrder, List.of(firstUser, secondUser));
        delegate.updateApprovalOrder(capplm, finalOrder);

        com.fasterxml.jackson.databind.JsonNode line =
                new ObjectMapper().readTree(detail.get()).path("approvalLine");
        assertThat(line.path("additionalApprovers").get(0).path("id").asText()).isEqualTo("E003");
        assertThat(line.path("additionalApprovers").get(0).path("name").asText())
                .isEqualTo("완료추가결재자");
        assertThat(line.path("additionalApprovers").get(0).path("rank").asText()).isEqualTo("차장");
        assertThat(line.path("teamLead").path("id").asText()).isEqualTo("E100");
        assertThat(line.path("departmentHead").path("id").asText()).isEqualTo("E101");
        assertThat(line.path("order"))
                .extracting(com.fasterxml.jackson.databind.JsonNode::asText)
                .containsExactly("E003", "E100", "E101");
    }

    @Test
    @DisplayName("replacePendingApproversInDetail: 완료 노드는 보존하고 미결재 노드와 추가 결재자 배열을 교체한다")
    void replacePendingApproversInDetail_완료노드보존_미결재노드교체() throws Exception {
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn(
                        "{\"approvalLine\":{\"teamLead\":{\"id\":\"E001\",\"name\":\"완료자\",\"rank\":\"부장\",\"date\":\"2026-09-01\"},"
                                + "\"departmentHead\":{\"id\":\"E002\",\"name\":\"기존미결재자\",\"rank\":\"차장\",\"date\":\"\"},"
                                + "\"additionalApprovers\":[{\"id\":\"E003\",\"name\":\"기존추가1\",\"rank\":\"과장\",\"date\":\"\"},{\"id\":\"E004\"}]}}");
        Cdecim completed = approver("E001", "2");
        Cdecim pendingFirst = approver("E100", "1");
        Cdecim pendingSecond = approver("E101", "1");
        CuserI firstUser = CuserI.builder().eno("E100").usrNm("새결재자1").ptCNm("과장").build();
        CuserI secondUser = CuserI.builder().eno("E101").usrNm("새결재자2").ptCNm("차장").build();

        delegate.replacePendingApproversInDetail(
                capplm,
                List.of(completed, pendingFirst, pendingSecond),
                List.of(firstUser, secondUser));

        org.mockito.ArgumentCaptor<String> jsonCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(capplm).updateDetailContent(jsonCaptor.capture());
        com.fasterxml.jackson.databind.JsonNode line =
                new ObjectMapper().readTree(jsonCaptor.getValue()).path("approvalLine");
        assertThat(line.path("teamLead").path("id").asText()).isEqualTo("E001");
        assertThat(line.path("teamLead").path("name").asText()).isEqualTo("완료자");
        assertThat(line.path("departmentHead").path("id").asText()).isEqualTo("E100");
        assertThat(line.path("departmentHead").path("name").asText()).isEqualTo("새결재자1");
        assertThat(line.path("departmentHead").path("rank").asText()).isEqualTo("과장");
        assertThat(line.path("additionalApprovers")).hasSize(1);
        assertThat(line.path("additionalApprovers").get(0).path("id").asText()).isEqualTo("E101");
        assertThat(line.path("additionalApprovers").get(0).path("name").asText())
                .isEqualTo("새결재자2");
        assertThat(line.path("additionalApprovers").get(0).path("rank").asText()).isEqualTo("차장");
    }

    private Cdecim approver(String eno, String status) {
        return Cdecim.builder()
                .dcdMngNo("APF-2026-00000001")
                .dcrSqnSno(1)
                .dcrEno(eno)
                .itPtlDcdStsC(status)
                .lstDcdYn("N")
                .dcdTpC(Cdecim.DECISION_TYPE_REQUEST)
                .build();
    }

    // ───────────────────────────────────────────────────────
    // updateApprovalOrder — 전체 미커버 메서드
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateApprovalOrder: 결재자 사번 순서를 order 배열로 기록한다")
    void updateApprovalOrder_정상_order배열기록() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf())
                .willReturn("{\"approvalLine\":{\"teamLead\":{\"id\":\"E001\"}}}");
        Cdecim first = mock(Cdecim.class);
        given(first.getDcrEno()).willReturn("E001");
        Cdecim second = mock(Cdecim.class);
        given(second.getDcrEno()).willReturn("E002");

        // Act
        delegate.updateApprovalOrder(capplm, List.of(first, second));

        // Assert: order 배열이 결재자 순서대로 기록됨
        verify(capplm)
                .updateDetailContent(
                        org.mockito.ArgumentMatchers.argThat(
                                json -> json.contains("\"order\":[\"E001\",\"E002\"]")));
    }

    @Test
    @DisplayName("updateApprovalOrder: 상세 JSON이 null 또는 공백이면 아무 작업도 하지 않는다")
    void updateApprovalOrder_JSON없음_미수행() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm nullJson = mock(Capplm.class);
        given(nullJson.getDcdReqInf()).willReturn(null);
        Capplm blankJson = mock(Capplm.class);
        given(blankJson.getDcdReqInf()).willReturn("  ");

        // Act
        delegate.updateApprovalOrder(nullJson, List.of());
        delegate.updateApprovalOrder(blankJson, List.of());

        // Assert
        verify(nullJson, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
        verify(blankJson, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("updateApprovalOrder: approvalLine이 객체가 아니면 아무 작업도 하지 않는다")
    void updateApprovalOrder_결재선객체아님_미수행() {
        // Arrange: approvalLine이 문자열 → ObjectNode 아님
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{\"approvalLine\":\"문자열\"}");

        // Act
        delegate.updateApprovalOrder(capplm, List.of());

        // Assert
        verify(capplm, org.mockito.Mockito.never())
                .updateDetailContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("updateApprovalOrder: JSON 파싱 실패 시 CustomGeneralException을 던진다")
    void updateApprovalOrder_JSON파싱실패_예외발생() {
        // Arrange
        ApprovalLineDelegate delegate = new ApprovalLineDelegate(new ObjectMapper());
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqInf()).willReturn("{잘못된JSON");
        given(capplm.getApfMngNo()).willReturn("APF-202600000012");

        // Act & Assert
        assertThatThrownBy(() -> delegate.updateApprovalOrder(capplm, List.of()))
                .isInstanceOf(CustomGeneralException.class);
    }
}
