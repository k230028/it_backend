package com.kdb.it.common.board.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.CblbcmL;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 게시물 엔티티 — TPRMPP_CBLBCM
 *
 * <p>답변글 트리는 NAC_UNQ_ID / GRP_SQN_SNO / NAC_LEV_MNG_SNO 3컬럼으로 표현한다.
 * 변경 시 {@link CblbcmL}에 이력이 자동 적재된다.</p>
 */
@LogTarget(entity = CblbcmL.class)
@Entity
@Table(name = "TPRMPP_CBLBCM", comment = "게시물")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Cblbcm extends BaseEntity {

    /** 게시물관리번호 PK. 형식: NAC-{YYYY}-{0001} */
    @Id
    @Column(name = "NAC_NO", nullable = false, length = 16, comment = "게시물번호")
    private String nacMngNo;

    @Column(name = "BLB_ID", nullable = false, length = 10, comment = "게시판ID")
    private String blbMngNo;

    @Column(name = "NAC_TTL", nullable = false, length = 300, comment = "게시물제목")
    private String nacNm;

    /** 본문 HTML — HtmlSanitizer.sanitize() 적용 의무, VARCHAR2(4000) */
    @Column(name = "NAC_CONE", length = 4000, comment = "게시물내용")
    private String nacCone;

    @Column(name = "NAC_INQ_NBR", nullable = false, comment = "게시물조회수")
    private Integer nacInqNbr;

    @Column(name = "NAC_ID", length = 16, comment = "게시물고유ID")
    private String nacId;

    @Column(name = "ANC_YN", nullable = false, length = 1, comment = "공지여부")
    private String ancYn;

    @Column(name = "SRE_USE_YN", nullable = false, length = 1, comment = "화면사용여부")
    private String sreYn;

    /** 공개 대상 부서코드 — NULL이면 전체 */
    @Column(name = "BBR_C", length = 3, comment = "부점코드")
    private String bbrC;

    @Column(name = "STT_DTM", comment = "시작일시")
    private LocalDate sttDt;

    @Column(name = "END_DTM", comment = "종료일시")
    private LocalDate endDt;

    @Column(name = "FL_APG_YN", nullable = false, length = 1, comment = "파일첨부여부")
    private String flApgYn;

    @Column(name = "APG_FL_NBR", nullable = false, comment = "첨부파일수")
    private Integer flNbr;

    @Column(name = "GRP_SQN_SNO", nullable = false, comment = "그룹순서일련번호")
    private Integer nacGrpSqn;

    /** 트리 깊이 (0=원글, 1=답글…) */
    @Column(name = "NAC_LEV_MNG_SNO", nullable = false, comment = "게시물레벨관리일련번호")
    private Integer nacGrpLev;

    @Column(name = "HRK_NAC_NO", length = 16, comment = "상위게시물번호")
    private String hrkNacNo;

    /**
     * 게시물 수정 커맨드
     *
     * @param nacNm    제목
     * @param nacCone  본문 HTML (sanitize 완료 값)
     * @param ancYn    공지여부
     * @param sreYn    화면여부
     * @param bbrC     공개 대상 부서코드
     * @param sttDt    공개 시작일자
     * @param endDt    공개 종료일자
     */
    public record UpdateCommand(
        String nacNm, String nacCone,
        String ancYn, String sreYn, String bbrC,
        LocalDate sttDt, LocalDate endDt
    ) {}

    /**
     * 게시물 내용을 수정합니다.
     *
     * <p>{@code nacCone}은 서비스 계층에서 {@code HtmlSanitizer.sanitize()}를 적용한
     * 값만 전달해야 합니다. JPA Dirty Checking으로 변경사항이 저장됩니다.</p>
     *
     * @param cmd 게시물 수정 커맨드
     */
    public void update(UpdateCommand cmd) {
        this.nacNm    = cmd.nacNm();
        this.nacCone  = cmd.nacCone();
        this.ancYn    = cmd.ancYn();
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
        this.nacId  = this.nacMngNo;
        this.nacGrpSqn = 0;
        this.nacGrpLev = 0;
    }

    /**
     * 그룹 정보 설정 — 답변글 등록 시
     *
     * @param parentGrpNo  부모의 NAC_UNQ_ID (그룹번호)
     * @param parentGrpSqn 부모의 NAC_GRP_SQN
     * @param parentGrpLev 부모의 NAC_GRP_LEV
     * @param parentPk     부모의 NAC_MNG_NO
     */
    public void initGroupAsReply(String parentGrpNo, int parentGrpSqn, int parentGrpLev, String parentPk) {
        this.nacId    = parentGrpNo;
        this.nacGrpSqn   = parentGrpSqn + 1;
        this.nacGrpLev   = parentGrpLev + 1;
        this.hrkNacNo    = parentPk;
    }
}
