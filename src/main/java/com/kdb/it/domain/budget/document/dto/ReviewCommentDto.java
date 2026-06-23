package com.kdb.it.domain.budget.document.dto;

import com.kdb.it.domain.budget.document.entity.Brivgm;
import com.kdb.it.domain.budget.document.util.DocVersionCodec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * 검토의견(TPRMPP_BRIVGM) 관련 DTO 클래스 모음
 *
 * <p>
 * 문서 검토의견({@link Brivgm})의 생성 요청 및 조회 응답 DTO를
 * 정적 중첩 클래스 형태로 관리합니다.
 * </p>
 */
public class ReviewCommentDto {

    /** 응답 DTO의 createdAt 필드 직렬화 포맷 (ISO-8601 유사, 타임존 없음) */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 검토의견 생성 요청 DTO
     *
     * <p>
     * {@code ivgTp}가 {@code I}(인라인)인 경우 {@code markId}, {@code qtdCone}을 함께 전달해야 합니다.
     * {@code G}(전반) 코멘트인 경우 두 필드는 {@code null}로 둡니다.
     * </p>
     */
    @Getter
    @NoArgsConstructor
    public static class CreateRequest {
        /** 문서버전 (예: 1.00, 1.01) */
        @NotNull
        private BigDecimal docVrs;
        /** 의견유형: I=인라인, G=전반 */
        @NotBlank
        @Pattern(regexp = "^[IG]$", message = "의견유형은 I(인라인) 또는 G(전반)이어야 합니다")
        private String rplOpnnTc;
        /** 의견내용 (CLOB) */
        @NotBlank
        private String ivgOpnnCone;
        /** 인라인 전용 - Tiptap Mark ID */
        private String markId;
        /** 인라인 전용 - 드래그 선택 텍스트 스냅샷 */
        private String qtdCone;

        /**
         * CreateRequest를 {@link Brivgm} 엔티티로 변환합니다.
         *
         * @param docMngNo 대상 문서관리번호
         * @return 영속화 전 상태의 Brivgm 엔티티
         */
        public Brivgm toEntity(String docMngNo) {
            // 화면 소수 버전 → 저장 정수 버전(× 100). Brdocm 버전 키와 동일 규약으로 정합성 유지.
            return Brivgm.create(docMngNo, DocVersionCodec.toStored(docVrs), rplOpnnTc, ivgOpnnCone, markId, qtdCone);
        }
    }

    /**
     * 검토의견 조회 응답 DTO
     *
     * <p>
     * 작성자 사번({@code authorEno})은 {@link Brivgm}의 {@code FST_ENR_USID}에서 가져오며,
     * 작성자 이름({@code authorName})은 별도 조회(TPRMPP_CUSERI JOIN)하여 주입합니다.
     * 후속 과제: 프론트엔드의 {@code ReviewComment.authorTeam} 임시값을 제거할 수 있도록
     *       조직 테이블(TPRMPP_CORGNI) 조인 기반 작성자 팀명 응답 필드를 추가해야 합니다.
     * </p>
     */
    @Getter
    public static class Response {
        /** 의견일련번호 */
        private final Long ipmOpnnSno;
        /** 문서관리번호 */
        private final String docMngNo;
        /** 문서버전 */
        private final BigDecimal docVrsSno;
        /** 의견유형 (I=인라인, G=전반) */
        private final String rplOpnnTc;
        /** 의견내용 */
        private final String ivgOpnnCone;
        /** Tiptap 표시 ID (인라인 전용) */
        private final String rfrId;
        /** 인용내용 (인라인 전용) */
        private final String rfrCone;
        /** 완료여부 (N=미완료, Y=완료) */
        private final String rslvYn;
        /** 작성자 사번 (FST_ENR_USID) */
        private final String authorEno;
        /** 작성자 이름 (CuserI JOIN 결과) */
        private final String authorName;
        /** 생성일시 (yyyy-MM-dd'T'HH:mm:ss 포맷 문자열) */
        private final String createdAt;

        /**
         * {@link Brivgm} 엔티티와 작성자 이름으로 Response를 생성합니다.
         *
         * @param e          검토의견 엔티티
         * @param authorName 작성자 이름 (미조회 시 null 허용)
         */
        public Response(Brivgm e, String authorName) {
            this.ipmOpnnSno = e.getIpmOpnnSno();
            this.docMngNo   = e.getDocMngNo();
            // 저장 정수 버전 → 화면 소수 버전(÷ 100)
            this.docVrsSno  = DocVersionCodec.toDisplay(e.getDocVrsSno());
            this.rplOpnnTc  = e.getRplOpnnTc();
            this.ivgOpnnCone = e.getIvgOpnnCone();
            this.rfrId      = e.getRfrId();
            this.rfrCone    = e.getRfrCone();
            this.rslvYn     = e.getFsgYn();
            this.authorEno  = e.getFstEnrUsid();
            this.authorName = authorName;
            this.createdAt  = e.getFstEnrDtm() != null
                    ? e.getFstEnrDtm().format(FORMATTER)
                    : null;
        }
    }
}
