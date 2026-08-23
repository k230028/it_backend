package com.kdb.it.common.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AdminCodeService 단위 테스트
 *
 * <p>Mockito로 Repository를 Mock 처리하여 Oracle DB 없이 관리자 공통코드 CRUD 비즈니스 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCodeServiceTest {

    @Mock private CodeRepository codeRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AdminCodeService adminCodeService;

    // =========================================================================
    // 공통코드 (Ccodem)
    // =========================================================================

    @Test
    @DisplayName("createCode - 중복 코드ID 존재 시 IllegalArgumentException 발생")
    void createCode_중복코드ID_예외발생() {
        // given: 이미 존재하는 코드ID
        String sttDt = "20260101";
        AdminDto.CodeRequest req =
                new AdminDto.CodeRequest(
                        "CODE001", "001", "코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, sttDt,
                        null, 1);
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CODE001", "001", sttDt)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminCodeService.createCode(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 코드입니다");
    }

    @Test
    @DisplayName("createCode - 정상 요청 시 codeRepository.save() 호출")
    void createCode_정상요청_저장호출() {
        // given
        String sttDt = "20260101";
        AdminDto.CodeRequest req =
                new AdminDto.CodeRequest(
                        "CODE002", "001", "코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, sttDt,
                        null, 1);
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CODE002", "001", sttDt)).willReturn(false);

        // when
        adminCodeService.createCode(req);

        // then
        verify(codeRepository, times(1)).save(any(Ccodem.class));
    }

    @Test
    @DisplayName("updateCode - 미존재 코드ID 수정 시 IllegalArgumentException 발생")
    void updateCode_미존재코드ID_예외발생() {
        // given
        String sttDt = "20260101";
        AdminDto.CodeRequest req =
                new AdminDto.CodeRequest(
                        "NONE", "001", "코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, sttDt,
                        null, 1);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("NONE", "001", sttDt, "N"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminCodeService.updateCode("NONE", "001", sttDt, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 코드입니다");
    }

    @Test
    @DisplayName("deleteCode - 정상 삭제 시 code.delete() 호출 (Soft Delete)")
    void deleteCode_정상삭제_SoftDelete() {
        // given
        String sttDt = "20260101";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).build();
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N"))
                .willReturn(Optional.of(code));

        // when
        adminCodeService.deleteCode("CODE001", "001", sttDt);

        // then: DEL_YN='Y' 처리 검증
        assertThat(code.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("bulkUpsertCodes - 신규/수정 건수를 정확히 반환한다")
    void bulkUpsertCodes_신규수정건수반환() {
        // given: CODE001은 기존 존재, CODE002는 신규
        String sttDt = "20260101";
        AdminDto.CodeRequest req1 =
                new AdminDto.CodeRequest(
                        "CODE001", "001", "코드1", null, null, null, null, null, null, null, sttDt,
                        null, 1);
        AdminDto.CodeRequest req2 =
                new AdminDto.CodeRequest(
                        "CODE002", "002", "코드2", null, null, null, null, null, null, null, sttDt,
                        null, 2);
        AdminDto.BulkCodeRequest bulkReq = new AdminDto.BulkCodeRequest(List.of(req1, req2));

        Ccodem existingCode = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).build();
        given(codeRepository.findAllByCIdIn(anyCollection())).willReturn(List.of(existingCode));

        // when
        var result = adminCodeService.bulkUpsertCodes(bulkReq);

        // then
        assertThat(result.get("updated")).isEqualTo(1);
        assertThat(result.get("created")).isEqualTo(1);
        verify(codeRepository, times(1)).saveAll(anyCollection());
    }

    @Test
    @DisplayName("bulkUpsertCodes - 삭제된 코드를 다시 올리면 그 행을 복원하고 created로 센다")
    void bulkUpsertCodes_삭제된코드_복원() {
        /*
         * 활성 행만 읽으면 삭제된 같은 복합키가 신규로 보여 merge(UPDATE) 경로로 들어가고,
         * 새 엔티티에는 GUID가 없어 NULL이 나가 NOT NULL 제약(ORA-01407)에 걸린다.
         */
        String sttDt = "20260101";
        AdminDto.CodeRequest req =
                new AdminDto.CodeRequest(
                        "CODE001", "001", "되살린 코드", null, null, null, null, null, null, null, sttDt,
                        null, 1);
        Ccodem deleted =
                Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).delYn("Y").build();
        given(codeRepository.findAllByCIdIn(anyCollection())).willReturn(List.of(deleted));

        var result = adminCodeService.bulkUpsertCodes(new AdminDto.BulkCodeRequest(List.of(req)));

        assertThat(deleted.getDelYn()).isEqualTo("N");
        assertThat(deleted.getCNm()).isEqualTo("되살린 코드");
        assertThat(result.get("created")).isEqualTo(1);
        assertThat(result.get("updated")).isZero();
    }

    @Test
    @DisplayName("updateCode - 복합키가 같으면 기존 코드의 일반 필드만 수정한다")
    void updateCode_동일키_기존항목수정() {
        String sttDt = "20260101";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).cNm("기존").build();
        AdminDto.CodeRequest req =
                new AdminDto.CodeRequest(
                        "CODE001", "001", "수정", "코드값명", "설명", "값", "상세", "타입", "타입설명", null, sttDt,
                        null, 2);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N"))
                .willReturn(Optional.of(code));

        adminCodeService.updateCode("CODE001", "001", sttDt, req);

        assertThat(code.getCNm()).isEqualTo("수정");
    }

    @Test
    @DisplayName("updateCode - 복합키 변경 시 기존 코드를 삭제하고 새 코드를 저장한다")
    void updateCode_키변경_새항목저장() {
        String sttDt = "20260101";
        String newSttDt = "20260201";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).build();
        AdminDto.CodeRequest req =
                new AdminDto.CodeRequest(
                        "CODE002", "002", "신규키", null, null, null, null, null, null, null, newSttDt,
                        null, 1);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N"))
                .willReturn(Optional.of(code));

        adminCodeService.updateCode("CODE001", "001", sttDt, req);

        assertThat(code.getDelYn()).isEqualTo("Y");
        verify(codeRepository).save(any(Ccodem.class));
    }

    @Test
    @DisplayName("updateCode - 변경 대상 복합키가 이미 있으면 저장을 거절한다")
    void updateCode_변경키중복_예외발생() {
        String sttDt = "20260101";
        String newSttDt = "20260201";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).build();
        AdminDto.CodeRequest req =
                new AdminDto.CodeRequest(
                        "CODE002", "002", "신규키", null, null, null, null, null, null, null, newSttDt,
                        null, 1);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N"))
                .willReturn(Optional.of(code));
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CODE002", "002", newSttDt))
                .willReturn(true);

        assertThatThrownBy(() -> adminCodeService.updateCode("CODE001", "001", sttDt, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 코드입니다");
    }

    @Test
    @DisplayName("createCode - 코드 키 필수값이 없으면 각각 예외를 반환한다")
    void createCode_필수키누락_예외발생() {
        String date = "20260101";
        AdminDto.CodeRequest noId =
                new AdminDto.CodeRequest(
                        " ", "001", null, null, null, null, null, null, null, null, date, null, 1);
        AdminDto.CodeRequest noValue =
                new AdminDto.CodeRequest(
                        "CODE", null, null, null, null, null, null, null, null, null, date, null,
                        1);
        AdminDto.CodeRequest noDate =
                new AdminDto.CodeRequest(
                        "CODE", "001", null, null, null, null, null, null, null, null, null, null,
                        1);

        assertThatThrownBy(() -> adminCodeService.createCode(noId)).hasMessageContaining("코드ID");
        assertThatThrownBy(() -> adminCodeService.createCode(noValue)).hasMessageContaining("코드값");
        assertThatThrownBy(() -> adminCodeService.createCode(noDate)).hasMessageContaining("시작일자");
    }

    @Test
    @DisplayName("getCodes - 활성 코드 목록을 반환하며 감사 필드를 이름으로 변환한다")
    void getCodes_활성코드목록반환() {
        // given
        Ccodem code = Ccodem.builder().cId("CODE001").cNm("코드1").build();
        given(codeRepository.findAllActive()).willReturn(List.of(code));
        given(userRepository.findNameViewsByEnoIn(any())).willReturn(Collections.emptyList());

        // when
        List<AdminDto.CodeResponse> result = adminCodeService.getCodes();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).cId()).isEqualTo("CODE001");
    }

    @Test
    @DisplayName("deleteCode - 미존재 코드 삭제 시 IllegalArgumentException 발생")
    void deleteCode_미존재코드_예외발생() {
        // given
        String sttDt = "20260101";
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("NONE", "001", sttDt, "N"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminCodeService.deleteCode("NONE", "001", sttDt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 코드입니다");
    }

    @Test
    @DisplayName("updateCode - 요청 복합키가 비어 있으면 path 복합키를 유지하고 일반 필드만 수정한다")
    void updateCode_요청키공백_path키유지() {
        // given: req의 PK 3개가 각각 null·공백이면 rename이 아니라 path PK 유지로 처리해야 한다
        String sttDt = "20260101";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).cNm("기존").build();
        AdminDto.CodeRequest req =
                new AdminDto.CodeRequest(
                        null, " ", "수정", null, null, null, null, null, null, null, " ", null, 1);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N"))
                .willReturn(Optional.of(code));

        // when
        adminCodeService.updateCode("CODE001", "001", sttDt, req);

        // then: 신규 행 저장 없이 기존 행만 갱신된다
        assertThat(code.getCNm()).isEqualTo("수정");
        assertThat(code.getDelYn()).isNotEqualTo("Y");
        verify(codeRepository, never()).save(any(Ccodem.class));
    }

    @Test
    @DisplayName("deleteCode - 코드 키 필수값이 없으면 각각 예외를 반환한다")
    void deleteCode_필수키누락_예외발생() {
        // given: null 과 공백을 모두 필수값 위반으로 처리해야 한다
        String date = "20260101";

        // when & then
        assertThatThrownBy(() -> adminCodeService.deleteCode(null, "001", date))
                .hasMessageContaining("코드ID");
        assertThatThrownBy(() -> adminCodeService.deleteCode("CODE", " ", date))
                .hasMessageContaining("코드값");
        assertThatThrownBy(() -> adminCodeService.deleteCode("CODE", "001", " "))
                .hasMessageContaining("시작일자");
    }

    @Test
    @DisplayName("getCodes - 감사 사번이 사용자명으로 치환되고 미등록 사번은 원문을 유지한다")
    void getCodes_감사사번_사용자명치환() {
        // given: 최초생성자만 사용자 테이블에 존재하고 마지막수정자는 미등록
        Ccodem code = Ccodem.builder().cId("CODE001").cNm("코드1").build();
        ReflectionTestUtils.setField(code, "fstEnrUsid", "10001");
        ReflectionTestUtils.setField(code, "lstChgUsid", "99999");
        given(codeRepository.findAllActive()).willReturn(List.of(code));
        given(userRepository.findNameViewsByEnoIn(any()))
                .willReturn(List.of(new NameView("10001", "홍길동")));

        // when
        List<AdminDto.CodeResponse> result = adminCodeService.getCodes();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fstEnrUsNm()).isEqualTo("홍길동");
        assertThat(result.get(0).lstChgUsNm()).isEqualTo("99999");
    }

    /** 사용자명 배치 조회 결과 스텁. */
    private record NameView(String eno, String usrNm) implements UserRepository.UserNameView {
        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public String getUsrNm() {
            return usrNm;
        }

        @Override
        public String getPtCNm() {
            return null;
        }
    }
}
