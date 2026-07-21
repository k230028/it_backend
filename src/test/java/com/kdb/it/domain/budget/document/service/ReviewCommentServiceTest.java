package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.document.dto.ReviewCommentDto;
import com.kdb.it.domain.budget.document.entity.Brivgm;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * 검토의견 서비스({@link ReviewCommentService}) 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class ReviewCommentServiceTest {

    private record NameView(String eno, String usrNm) implements UserRepository.UserNameView {
        @Override public String getEno() { return eno; }
        @Override public String getUsrNm() { return usrNm; }
    }

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
    void 코멘트_생성시_완료여부가_N으로_초기화된다() {
        // 준비 & 실행: 팩토리 메서드로 생성 (영속화/@PrePersist 이전 시점)
        var entity = Brivgm.create("DOC-2026-0009", new BigDecimal("1.00"),
                "G", "리뷰 코멘트", null, null);

        // 검증: 감사 로그(BrivgmL)는 BaseEntity @EntityListeners가 엔티티 자신의
        // @PrePersist보다 먼저 fsgYn을 스냅샷하므로, 생성 시점에 'N'이어야
        // TPRMPP_BRIVGL.FSG_YN(NOT NULL) 위반(ORA-01400)을 막을 수 있다.
        assertThat(entity.getFsgYn()).isEqualTo("N");
    }

    @Test
    void 코멘트_조회시_해당_버전의_미삭제_코멘트만_반환된다() {
        // 준비
        var comment = Brivgm.create("DOC-2026-0010", new BigDecimal("1.01"),
                "G", "전반 코멘트", null, null);
        given(brivgmRepository.findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(
                "DOC-2026-0010", new BigDecimal("101"), "N"))   // 화면 1.01 → 저장 정수 101(× 100)
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
        setFstEnrUsid(comment, "E12345");

        given(brivgmRepository.findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(
                "DOC-2026-0010", new BigDecimal("101"), "N"))   // 화면 1.01 → 저장 정수 101(× 100)
                .willReturn(List.of(comment));

        var user = new NameView("E12345", "홍길동");
        given(userRepository.findNameViewsByEnoIn(ArgumentMatchers.<java.util.Collection<String>>any()))
                .willReturn(List.of(user));

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
        setFstEnrUsid(comment, "UNKNOWN_ENO");

        given(brivgmRepository.findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(
                "DOC-2026-0010", new BigDecimal("101"), "N"))   // 화면 1.01 → 저장 정수 101(× 100)
                .willReturn(List.of(comment));
        given(userRepository.findNameViewsByEnoIn(ArgumentMatchers.<java.util.Collection<String>>any()))
                .willReturn(List.of());

        // 실행
        List<ReviewCommentDto.Response> result =
                reviewCommentService.getComments("DOC-2026-0010", new BigDecimal("1.01"));

        // 검증: 미존재 사용자는 사번(eno) 자체를 fallback으로 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAuthorName()).isEqualTo("UNKNOWN_ENO");
    }

    @Test
    void getComments_작성자명은_이름프로젝션_1회_배치조회하고_단건조회는_호출하지_않는다() {
        // 준비: 동일 사번(E001) 2건 + 다른 사번(E002) 1건 → 사번 집합은 {E001, E002}
        var c1 = Brivgm.create("DOC-1", new BigDecimal("0.01"), "G", "코멘트1", null, null);
        var c2 = Brivgm.create("DOC-1", new BigDecimal("0.01"), "G", "코멘트2", null, null);
        var c3 = Brivgm.create("DOC-1", new BigDecimal("0.01"), "G", "코멘트3", null, null);
        setFstEnrUsid(c1, "E001");
        setFstEnrUsid(c2, "E002");
        setFstEnrUsid(c3, "E001");
        given(brivgmRepository.findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(
                eq("DOC-1"), any(), eq("N")))
                .willReturn(List.of(c1, c2, c3));
        var u1 = new NameView("E001", "홍길동");
        var u2 = new NameView("E002", "김철수");
        given(userRepository.findNameViewsByEnoIn(ArgumentMatchers.<java.util.Collection<String>>any()))
                .willReturn(List.of(u1, u2));

        List<ReviewCommentDto.Response> result =
                reviewCommentService.getComments("DOC-1", new BigDecimal("0.01"));

        assertThat(result).hasSize(3);
        then(userRepository).should(times(1))
                .findNameViewsByEnoIn(ArgumentMatchers.<java.util.Collection<String>>any());
        then(userRepository).should(never()).findById(anyString());
        then(userRepository).should(never()).findNameViewByEno(anyString());
    }

    // 헬퍼: BaseEntity.fstEnrUsid를 리플렉션으로 주입 (JPA Auditing 대체)
    private void setFstEnrUsid(Brivgm comment, String eno) {
        try {
            var field = comment.getClass().getSuperclass().getDeclaredField("fstEnrUsid");
            field.setAccessible(true);
            field.set(comment, eno);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            BigDecimal docVrs, String itPtlRplOpnnTc, String ivgOpnnCone,
            String markId, String qtdCone) {
        try {
            var req = new ReviewCommentDto.CreateRequest();
            setField(req, "docVrs", docVrs);
            setField(req, "itPtlRplOpnnTc", itPtlRplOpnnTc);
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
