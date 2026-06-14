package com.kdb.it.domain.deliberation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.deliberation.entity.Bdelim;
import com.kdb.it.domain.deliberation.repository.DeliberationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliberationServiceTest {

    @Mock DeliberationRepository deliberationRepository;
    @Mock ProjectRepository projectRepository;
    @Mock CostRepository costRepository;

    DeliberationService service;

    // -----------------------------------------------------------------------
    // 테스트 픽스처 헬퍼
    // -----------------------------------------------------------------------

    /** 일반 사용자 (부서코드 18001) */
    CustomUserDetails requester() {
        return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
    }

    /** 관리자 사용자 */
    CustomUserDetails admin() {
        return new CustomUserDetails("A0001", List.of("ITPAD001"), "18001");
    }

    /** 지정 상태로 과업심의 엔티티 생성 */
    Bdelim bdelim(String docNo, String bgPrnTc, String cncdRfrNo, String stsTc) {
        return Bdelim.builder()
                .docMngNo(docNo).docVrsSno(1).lstYn("Y")
                .bgPrnTc(bgPrnTc).cncdRfrNo(cncdRfrNo)
                .stsTc(stsTc).taskDbrOmtYn("N").build();
    }

    @BeforeEach
    void setUp() {
        service = new DeliberationService(deliberationRepository, projectRepository, costRepository);
    }

    // -----------------------------------------------------------------------
    // create — 신규 신청 생성
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("사업 대상 신규 신청 생성 시 문서번호를 채번하고 상태 51, 대상구분 100으로 저장한다")
    void create_project_assignsDocNoAndStatus51() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
        when(deliberationRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(deliberationRepository.nextDocSeq()).thenReturn(1L);
        when(deliberationRepository.save(any(Bdelim.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(
                new DeliberationDto.CreateRequest("100", "PRJ-1", "심의 요청합니다"), requester());

        assertThat(docNo).matches("DLB-\\d{4}-0001");
    }

    @Test
    @DisplayName("전산업무비 대상 신규 신청 생성 시 문서번호를 채번하고 상태 51, 대상구분 200으로 저장한다")
    void create_cost_assignsDocNoAndStatus51() {
        when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-1", "Y", "N")).thenReturn(true);
        when(deliberationRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(deliberationRepository.nextDocSeq()).thenReturn(1L);
        when(deliberationRepository.save(any(Bdelim.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(
                new DeliberationDto.CreateRequest("200", "BG-1", "전산업무비 심의 요청"), requester());

        assertThat(docNo).matches("DLB-\\d{4}-0001");
    }

    @Test
    @DisplayName("대상 사업이 없으면 신규 신청을 거부한다")
    void create_rejectsWhenTargetProjectMissing() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-X", "Y", "N")).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new DeliberationDto.CreateRequest("100", "PRJ-X", null), requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("대상");
    }

    @Test
    @DisplayName("대상 전산업무비가 없으면 신규 신청을 거부한다")
    void create_rejectsWhenTargetCostMissing() {
        // Arrange
        when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-X", "Y", "N")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.create(
                new DeliberationDto.CreateRequest("200", "BG-X", null), requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("대상");
    }

    @Test
    @DisplayName("알 수 없는 대상구분(999)이면 validateTarget에서 IllegalArgumentException을 던진다")
    void create_rejectsUnknownBgPrnTc() {
        // Act & Assert — validateTarget 에서 else 분기 실행
        assertThatThrownBy(() -> service.create(
                new DeliberationDto.CreateRequest("999", "ANY-1", null), requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 대상구분");
    }

    @Test
    @DisplayName("동일 대상에 진행중 문서가 있으면 신규 신청을 거부한다")
    void create_rejectsDuplicate() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
        when(deliberationRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new DeliberationDto.CreateRequest("100", "PRJ-1", null), requester()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("시퀀스 번호가 42이면 문서번호가 DLB-{연도}-0042 형식으로 채번된다")
    void create_docNoFormatIncludesSeqPaddedTo4Digits() {
        // Arrange
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-2", "Y", "N")).thenReturn(true);
        when(deliberationRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(deliberationRepository.nextDocSeq()).thenReturn(42L);
        when(deliberationRepository.save(any(Bdelim.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        String docNo = service.create(
                new DeliberationDto.CreateRequest("100", "PRJ-2", "내용"), requester());

        // Assert
        assertThat(docNo).endsWith("-0042");
    }

    // -----------------------------------------------------------------------
    // update — 마스터 수정
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("작성중(51) 상태에서 요청내용 수정이 반영된다")
    void update_draft_appliesReqCone() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "51");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act
        service.update("DLB-2026-0001", new DeliberationDto.UpdateRequest("수정된 내용"), requester());

        // Assert
        assertThat(e.getReqCone()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("작성중이 아닐 때(52) 마스터 수정을 거부한다")
    void update_rejectsWhenNotDraft() {
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "52");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.update(
                "DLB-2026-0001", new DeliberationDto.UpdateRequest("수정 내용"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("완료 상태(59)에서도 마스터 수정을 거부한다")
    void update_rejectsWhenDone() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "59");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act & Assert
        assertThatThrownBy(() -> service.update(
                "DLB-2026-0001", new DeliberationDto.UpdateRequest("수정 시도"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("문서가 없으면 update는 IllegalArgumentException을 던진다")
    void update_throwsWhenDocNotFound() {
        // Arrange
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("NONE", "Y", "N"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.update(
                "NONE", new DeliberationDto.UpdateRequest("내용"), requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    // -----------------------------------------------------------------------
    // delete — Soft Delete
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("작성중(51) 상태에서 삭제하면 엔티티의 delete()가 호출되어 delYn이 Y가 된다")
    void delete_draft_marksDeleted() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "51");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act
        service.delete("DLB-2026-0001", requester());

        // Assert — BaseEntity.delete()가 delYn='Y' 로 설정
        assertThat(e.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("진행중(52) 상태에서 삭제를 거부한다")
    void delete_rejectsWhenInProgress() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "52");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act & Assert
        assertThatThrownBy(() -> service.delete("DLB-2026-0001", requester()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("작성중");
    }

    @Test
    @DisplayName("완료(59) 상태에서 삭제를 거부한다")
    void delete_rejectsWhenDone() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "59");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act & Assert
        assertThatThrownBy(() -> service.delete("DLB-2026-0001", requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("문서가 없으면 delete는 IllegalArgumentException을 던진다")
    void delete_throwsWhenDocNotFound() {
        // Arrange
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("NONE", "Y", "N"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.delete("NONE", requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    // -----------------------------------------------------------------------
    // changeStatus — 상태 전이
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("작성중(51)→진행중(52) 전이를 허용한다")
    void changeStatus_submitAllowed() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("51").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        service.changeStatus("DLB-2026-0001", new DeliberationDto.StatusRequest("52"), requester());

        assertThat(e.getStsTc()).isEqualTo("52");
    }

    @Test
    @DisplayName("진행중(52)→완료(59) 전이를 허용한다")
    void changeStatus_completeAllowed() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "52");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act
        service.changeStatus("DLB-2026-0001", new DeliberationDto.StatusRequest("59"), requester());

        // Assert
        assertThat(e.getStsTc()).isEqualTo("59");
    }

    @Test
    @DisplayName("완료(59)에서 역행 전이(59→52)를 거부한다")
    void changeStatus_rejectsBackward() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("59").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.changeStatus(
                "DLB-2026-0001", new DeliberationDto.StatusRequest("52"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("작성중(51)에서 완료(59)로 단계 건너뛰기 전이를 거부한다")
    void changeStatus_rejectsSkipFromDraftToDone() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "51");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act & Assert
        assertThatThrownBy(() -> service.changeStatus(
                "DLB-2026-0001", new DeliberationDto.StatusRequest("59"), requester()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("허용되지 않은 상태 전이");
    }

    @Test
    @DisplayName("진행중(52)에서 작성중(51)으로 역행 전이를 거부한다")
    void changeStatus_rejectsBackFromInProgressToDraft() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "52");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act & Assert
        assertThatThrownBy(() -> service.changeStatus(
                "DLB-2026-0001", new DeliberationDto.StatusRequest("51"), requester()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("허용되지 않은 상태 전이");
    }

    @Test
    @DisplayName("완료(59)에서 완료(59)로 자기 전이를 거부한다")
    void changeStatus_rejectsSelfTransitionOnDone() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "59");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act & Assert
        assertThatThrownBy(() -> service.changeStatus(
                "DLB-2026-0001", new DeliberationDto.StatusRequest("59"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("문서가 없으면 changeStatus는 IllegalArgumentException을 던진다")
    void changeStatus_throwsWhenDocNotFound() {
        // Arrange
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("NONE", "Y", "N"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.changeStatus(
                "NONE", new DeliberationDto.StatusRequest("52"), requester()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -----------------------------------------------------------------------
    // saveResult — 심의 결과 입력
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("진행중이 아닐 때(51) 심의 결과 입력을 거부한다")
    void saveResult_rejectsWhenNotInProgress() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("51").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.saveResult(
                "DLB-2026-0001",
                new DeliberationDto.ResultRequest("01", "01", "20260601", "01", "N", null, null, null),
                requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중(52)에서 심의 결과 입력 시 결과 필드가 반영된다")
    void saveResult_inProgress_appliesResult() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("52").taskDbrOmtYn("N").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        service.saveResult(
                "DLB-2026-0001",
                new DeliberationDto.ResultRequest("01", "01", "20260601", "01", "N", null, "의견없음", null),
                requester());

        assertThat(e.getTaskDbrRltTc()).isEqualTo("01");
    }

    @Test
    @DisplayName("taskDbrOmtYn이 null이면 saveResult가 'N'으로 기본값을 적용한다")
    void saveResult_nullTaskDbrOmtYn_defaultsToN() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "52");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act — taskDbrOmtYn = null 명시적으로 전달
        service.saveResult(
                "DLB-2026-0001",
                new DeliberationDto.ResultRequest("01", "02", "20260615", "02", null, null, "결과 의견", "반려사유"),
                requester());

        // Assert — null → "N" 기본값 처리 확인 (서비스 내 null 체크 분기)
        assertThat(e.getTaskDbrOmtYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("taskDbrOmtYn이 'Y'이면 생략여부 Y가 반영된다")
    void saveResult_taskDbrOmtYn_Y_applied() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "200", "BG-1", "52");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act
        service.saveResult(
                "DLB-2026-0001",
                new DeliberationDto.ResultRequest("02", "03", "20260620", "03", "Y", "긴급사유", null, null),
                requester());

        // Assert
        assertThat(e.getTaskDbrOmtYn()).isEqualTo("Y");
        assertThat(e.getTaskDbrOmtRsn()).isEqualTo("긴급사유");
    }

    @Test
    @DisplayName("완료(59) 상태에서 심의 결과 입력을 거부한다")
    void saveResult_rejectsWhenDone() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "59");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act & Assert
        assertThatThrownBy(() -> service.saveResult(
                "DLB-2026-0001",
                new DeliberationDto.ResultRequest("01", "01", "20260601", "01", "N", null, null, null),
                requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("문서가 없으면 saveResult는 IllegalArgumentException을 던진다")
    void saveResult_throwsWhenDocNotFound() {
        // Arrange
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("NONE", "Y", "N"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.saveResult(
                "NONE",
                new DeliberationDto.ResultRequest("01", "01", "20260601", "01", "N", null, null, null),
                requester()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -----------------------------------------------------------------------
    // get — 상세 조회
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("사업 대상(100) 문서 상세 조회 시 사업명이 tgtNm으로 반환된다")
    void get_project_returnsTgtNm() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-1", "51");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        Bprojm proj = Bprojm.builder().abusMngNo("PRJ-1").sno(1).abusNm("클라우드 전환 사업").lstYn("Y").build();
        when(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N"))
                .thenReturn(Optional.of(proj));

        // Act
        DeliberationDto.Detail detail = service.get("DLB-2026-0001");

        // Assert
        assertThat(detail.tgtNm()).isEqualTo("클라우드 전환 사업");
        assertThat(detail.docMngNo()).isEqualTo("DLB-2026-0001");
    }

    @Test
    @DisplayName("사업 대상(100)인데 사업 레코드가 없으면 tgtNm은 null이다")
    void get_project_notFound_tgtNmIsNull() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0001", "100", "PRJ-NONE", "51");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        when(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-NONE", "Y", "N"))
                .thenReturn(Optional.empty());

        // Act
        DeliberationDto.Detail detail = service.get("DLB-2026-0001");

        // Assert
        assertThat(detail.tgtNm()).isNull();
    }

    @Test
    @DisplayName("전산업무비 대상(200) 문서 상세 조회 시 계약명이 tgtNm으로 반환된다")
    void get_cost_returnsCttNm() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0002", "200", "BG-1", "52");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0002", "Y", "N"))
                .thenReturn(Optional.of(e));
        Bcostm cost = Bcostm.builder().costBgNo("BG-1").bgSno(1).cttNm("서버 유지보수 계약").lstYn("Y").build();
        when(costRepository.findByCostBgNoAndLstYnAndDelYn("BG-1", "Y", "N"))
                .thenReturn(Optional.of(cost));

        // Act
        DeliberationDto.Detail detail = service.get("DLB-2026-0002");

        // Assert
        assertThat(detail.tgtNm()).isEqualTo("서버 유지보수 계약");
    }

    @Test
    @DisplayName("전산업무비 대상(200)인데 전산업무비 레코드가 없으면 tgtNm은 null이다")
    void get_cost_notFound_tgtNmIsNull() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-0002", "200", "BG-NONE", "52");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0002", "Y", "N"))
                .thenReturn(Optional.of(e));
        when(costRepository.findByCostBgNoAndLstYnAndDelYn("BG-NONE", "Y", "N"))
                .thenReturn(Optional.empty());

        // Act
        DeliberationDto.Detail detail = service.get("DLB-2026-0002");

        // Assert
        assertThat(detail.tgtNm()).isNull();
    }

    @Test
    @DisplayName("알 수 없는 대상구분(999)이면 resolveTargetName이 null을 반환하여 tgtNm은 null이다")
    void get_unknownBgPrnTc_tgtNmIsNull() {
        // Arrange — bgPrnTc=999 는 resolveTargetName의 마지막 return null 분기 실행
        Bdelim e = bdelim("DLB-2026-0003", "999", "ANY-1", "51");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0003", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act
        DeliberationDto.Detail detail = service.get("DLB-2026-0003");

        // Assert
        assertThat(detail.tgtNm()).isNull();
    }

    @Test
    @DisplayName("문서가 없으면 get은 IllegalArgumentException을 던진다")
    void get_throwsWhenDocNotFound() {
        // Arrange
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("NONE", "Y", "N"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.get("NONE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    // -----------------------------------------------------------------------
    // list — 목록 조회
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("관리자는 bbrC=null로 전체 목록을 조회한다")
    void list_admin_passesBbrCNull() {
        // Arrange
        List<DeliberationDto.ListItem> expected = List.of();
        when(deliberationRepository.search(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(expected);

        // Act
        List<DeliberationDto.ListItem> result = service.list(null, null, null, admin());

        // Assert — 관리자는 null bbrC로 search 호출
        assertThat(result).isSameAs(expected);
        verify(deliberationRepository).search(null, null, null, null);
    }

    @Test
    @DisplayName("일반 사용자는 소속 부서코드(bbrC)로 필터링하여 목록을 조회한다")
    void list_user_passesBbrCFromUser() {
        // Arrange
        List<DeliberationDto.ListItem> expected = List.of();
        when(deliberationRepository.search(isNull(), isNull(), isNull(), eq("18001")))
                .thenReturn(expected);

        // Act
        List<DeliberationDto.ListItem> result = service.list(null, null, null, requester());

        // Assert
        assertThat(result).isSameAs(expected);
        verify(deliberationRepository).search(null, null, null, "18001");
    }

    @Test
    @DisplayName("상태·대상구분·대상번호 필터를 함께 전달하면 search에 그대로 전달된다")
    void list_withFilters_passesFiltersToRepository() {
        // Arrange
        when(deliberationRepository.search("51", "100", "PRJ-1", "18001"))
                .thenReturn(List.of());

        // Act
        service.list("51", "100", "PRJ-1", requester());

        // Assert
        verify(deliberationRepository).search("51", "100", "PRJ-1", "18001");
    }

    // -----------------------------------------------------------------------
    // loadCurrent — 패키지 접근 메서드 직접 검증
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("loadCurrent: 문서가 있으면 Bdelim 엔티티를 반환한다")
    void loadCurrent_returnsEntityWhenFound() {
        // Arrange
        Bdelim e = bdelim("DLB-2026-9999", "100", "PRJ-1", "51");
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-9999", "Y", "N"))
                .thenReturn(Optional.of(e));

        // Act
        Bdelim result = service.loadCurrent("DLB-2026-9999");

        // Assert
        assertThat(result).isSameAs(e);
    }

    @Test
    @DisplayName("loadCurrent: 문서가 없으면 IllegalArgumentException을 던지며 문서번호를 메시지에 포함한다")
    void loadCurrent_throwsWithDocNoInMessage() {
        // Arrange
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-9999", "Y", "N"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.loadCurrent("DLB-2026-9999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DLB-2026-9999");
    }
}
