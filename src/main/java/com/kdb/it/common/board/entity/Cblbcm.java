package com.kdb.it.common.board.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.CblbcmL;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 게시물 엔티티 — TAAABB_CBLBCM
 *
 * <p>답변글 트리는 NAC_GRP_NO / NAC_GRP_SQN / NAC_GRP_LEV 3컬럼으로 표현한다.
 * 변경 시 {@link CblbcmL}에 이력이 자동 적재된다.</p>
 */
@LogTarget(entity = CblbcmL.class)
@Entity
@Table(name = "TAAABB_CBLBCM", comment = "게시물")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Cblbcm extends BaseEntity {

    /** 게시물관리번호 PK. 형식: NAC-{YYYY}-{0001} */
    @Id
    @Column(name = "NAC_MNG_NO", nullable = false, length = 32, comment = "게시물관리번호")
    private String nacMngNo;

    @Column(name = "BLB_MNG_NO", nullable = false, length = 32, comment = "게시판관리번호")
    private String blbMngNo;

    @Column(name = "NAC_NM", nullable = false, length = 300, comment = "게시물명")
    private String nacNm;

    /** 본문 HTML — HtmlSanitizer.sanitize() 적용 의무 */
    @Lob
    @Column(name = "NAC_CONE", comment = "게시물내용")
    private String nacCone;

    @Column(name = "NAC_INQ_NBR", nullable = false, comment = "게시물조회수")
    private Integer nacInqNbr;

    @Column(name = "NAC_TP", length = 32, comment = "게시물유형")
    private String nacTp;

    @Column(name = "KD_C", length = 32, comment = "종류코드")
    private String kdC;

    @Column(name = "PRIT_C", nullable = false, length = 32, comment = "중요도코드")
    private String pritC;

    @Column(name = "HRK_FXN_YN", nullable = false, length = 1, comment = "상위고정여부")
    private String hrkFxnYn;

    @Column(name = "SRE_YN", nullable = false, length = 1, comment = "화면여부")
    private String sreYn;

    /** 공개 대상 부서코드 — NULL이면 전체 */
    @Column(name = "BBR_C", length = 8, comment = "부점코드")
    private String bbrC;

    @Column(name = "STT_DT", comment = "시작일자")
    private LocalDate sttDt;

    @Column(name = "END_DT", comment = "종료일자")
    private LocalDate endDt;

    @Column(name = "FL_APG_YN", nullable = false, length = 1, comment = "파일첨부여부")
    private String flApgYn;

    @Column(name = "FL_NBR", nullable = false, comment = "파일수")
    private Integer flNbr;

    /** 그룹번호 — 최상위 글의 NAC_MNG_NO */
    @Column(name = "NAC_GRP_NO", nullable = false, length = 32, comment = "게시물그룹번호")
    private String nacGrpNo;

    @Column(name = "NAC_GRP_SQN", nullable = false, comment = "게시물그룹순서")
    private Integer nacGrpSqn;

    /** 트리 깊이 (0=원글, 1=답글…) */
    @Column(name = "NAC_GRP_LEV", nullable = false, comment = "게시물그룹레벨")
    private Integer nacGrpLev;

    @Column(name = "HRK_NAC_MNG_NO", length = 32, comment = "상위게시물관리번호")
    private String hrkNacMngNo;

    /**
     * 게시물 수정 커맨드
     *
     * @param nacNm    제목
     * @param nacCone  본문 HTML (sanitize 완료 값)
     * @param nacTp    게시물유형 코드
     * @param kdC      종류 코드
     * @param pritC    중요도 코드
     * @param hrkFxnYn 상위고정여부
     * @param sreYn    화면여부
     * @param bbrC     공개 대상 부서코드
     * @param sttDt    공개 시작일자
     * @param endDt    공개 종료일자
     */
    public record UpdateCommand(
        String nacNm, String nacCone, String nacTp, String kdC, String pritC,
        String hrkFxnYn, String sreYn, String bbrC,
        LocalDate sttDt, LocalDate endDt
    ) {}

    public void update(UpdateCommand cmd) {
        this.nacNm    = cmd.nacNm();
        this.nacCone  = cmd.nacCone();
        this.nacTp    = cmd.nacTp();
        this.kdC      = cmd.kdC();
        this.pritC    = cmd.pritC();
        this.hrkFxnYn = cmd.hrkFxnYn();
        this.sreYn    = cmd.sreYn();
        this.bbrC     = cmd.bbrC();
        this.sttDt    = cmd.sttDt();
        this.endDt    = cmd.endDt();
    }

    /** 조회수 1 증가 */
    public void incrementViewCount() {
        this.nacInqNbr = this.nacInqNbr + 1;
    }

    /** 첨부파일 캐시 갱신 */
    public void updateFileCache(boolean hasFile, int fileCount) {
        this.flApgYn = hasFile ? "Y" : "N";
        this.flNbr   = fileCount;
    }

    /** 그룹 정보 설정 — 원글 등록 시 */
    public void initGroupAsRoot() {
        this.nacGrpNo  = this.nacMngNo;
        this.nacGrpSqn = 0;
        this.nacGrpLev = 0;
    }

    /**
     * 그룹 정보 설정 — 답변글 등록 시
     *
     * @param parentGrpNo  부모의 NAC_GRP_NO
     * @param parentGrpSqn 부모의 NAC_GRP_SQN
     * @param parentGrpLev 부모의 NAC_GRP_LEV
     * @param parentPk     부모의 NAC_MNG_NO
     */
    public void initGroupAsReply(String parentGrpNo, int parentGrpSqn, int parentGrpLev, String parentPk) {
        this.nacGrpNo    = parentGrpNo;
        this.nacGrpSqn   = parentGrpSqn + 1;
        this.nacGrpLev   = parentGrpLev + 1;
        this.hrkNacMngNo = parentPk;
    }
}
