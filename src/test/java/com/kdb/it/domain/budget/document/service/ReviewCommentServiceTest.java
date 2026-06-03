package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.document.dto.ReviewCommentDto;
import com.kdb.it.domain.budget.document.entity.Brivgm;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * 검토의견 서비스({@link ReviewCommentService}) 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class ReviewCommentServiceTest {

    @Mock BrivgmRepository brivgmRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ReviewCommentService reviewCommentService;

    @Test
    void 코멘트_추가시_리포지토리_save가_호출된다() {
        // 준비: save 호출 시 반환될 엔티티 구성
        var entity = Brivgm.create("DOC-2026-0010", new BigDecimal("1.01"),
                "G", "테스트 코멘트", null, null);
        given(brivgmRepository.save(any(Brivgm.class))).willReturn(entity);

        // 실행
        reviewCommentService.addComment("DOC-2026-0010",
                createRequest(new BigDecimal("1.01"), "G", "테스트 코멘트", null, null));

        // 검증: save 호출 여부 확인
        then(brivgmRepository).should().save(any(Brivgm.class));
    }

    @Test
    void 코멘트_조회시_해당_버전의_미삭제_코멘트만_반환된다() {
        // 준비
        var comment = Brivgm.create("DOC-2026-0010", new BigDecimal("1.01"),
                "G", "전반 코멘트", null, null);
        given(brivgmRepository.findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(
                "DOC-2026-0010", new BigDecimal("1.01"), "N"))
                .willReturn(List.of(comment));

        // 실행
        List<ReviewCommentDto.Response> result =
                reviewCommentService.getComments("DOC-2026-0010", new BigDecimal("1.01"));

        // 검증
        assertThat(result).hasSize(1);
    }

    @Test
    void 코멘트_조회시_작성자_사번으로_사용자명을_조회한다() {
        // 준비: FST_ENR_USID 가 설정된 코멘트 (JPA Auditing 대신 리플렉션으로 주입)
        var comment = Brivgm.create("DOC-2026-0010", new BigDecimal("1.01"),
                "G", "작성자 이름 확인용 코멘트", null, null);
        try {
            var field = comment.getClass().getSuperclass().getDeclaredField("fstEnrUsid");
            field.setAccessible(true);
            field.set(comment, "E12345");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        given(brivgmRepository.findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(
                "DOC-2026-0010", new BigDecimal("1.01"), "N"))
                .willReturn(List.of(comment));

        var user = CuserI.builder().eno("E12345").usrNm("홍길동").build();
        given(userRepository.findById("E12345")).willReturn(Optional.of(user));

        // 실행
        List<ReviewCommentDto.Response> result =
                reviewCommentService.getComments("DOC-2026-0010", new BigDecimal("1.01"));

        // 검증: 사번이 아닌 사용자명이 반환되어야 함
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAuthorName()).isEqualTo("홍길동");
    }

    @Test
    void 사용자_미존재시_사번을_그대로_반환한다() {
        // 준비
        var comment = Brivgm.create("DOC-2026-0010", new BigDecimal("1.01"),
                "G", "코멘트", null, null);
        try {
            var field = comment.getClass().getSuperclass().getDeclaredField("fstEnrUsid");
            field.setAccessible(true);
            field.set(comment, "UNKNOWN_ENO");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        given(brivgmRepository.findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(
                "DOC-2026-0010", new BigDecimal("1.01"), "N"))
                .willReturn(List.of(comment));
        given(userRepository.findById("UNKNOWN_ENO")).willReturn(Optional.empty());

        // 실행
        List<ReviewCommentDto.Response> result =
                reviewCommentService.getComments("DOC-2026-0010", new BigDecimal("1.01"));

        // 검증: 미존재 사용자는 사번(eno) 자체를 fallback으로 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAuthorName()).isEqualTo("UNKNOWN_ENO");
    }

    @Test
    void 존재하지_않는_코멘트_해결처리시_예외가_발생한다() {
        // 준비: 빈 Optional 반환 (docMngNo 검증 포함)
        given(brivgmRepository.findByIpmOpnnSnoAndDocMngNoAndDelYn(anyLong(), eq("DOC-2026-0010"), eq("N")))
                .willReturn(Optional.empty());

        // 실행 & 검증: 404 응답을 위해 ResponseStatusException 이 발생해야 한다
        assertThatThrownBy(() -> reviewCommentService.resolveComment("DOC-2026-0010", 999L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 코멘트_해결처리시_rslvYn이_Y로_변경된다() {
        // 준비
        var comment = Brivgm.create("DOC-2026-0010", new BigDecimal("1.01"),
                "G", "코멘트", null, null);
        given(brivgmRepository.findByIpmOpnnSnoAndDocMngNoAndDelYn(anyLong(), anyString(), eq("N")))
                .willReturn(Optional.of(comment));

        // 실행
        reviewCommentService.resolveComment("DOC-2026-0010", 1L);

        // 검증
        assertThat(comment.getFsgYn()).isEqualTo("Y");
    }

    // 헬퍼: CreateRequest 인스턴스를 reflection으로 생성
    private ReviewCommentDto.CreateRequest createRequest(
            BigDecimal docVrs, String rplOpnnTc, String ivgOpnnCone,
            String markId, String qtdCone) {
        try {
            var req = new ReviewCommentDto.CreateRequest();
            setField(req, "docVrs", docVrs);
            setField(req, "rplOpnnTc", rplOpnnTc);
            setField(req, "ivgOpnnCone", ivgOpnnCone);
            setField(req, "markId", markId);
            setField(req, "qtdCone", qtdCone);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
