package com.kdb.it.domain.budget.cost.dto;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 전산업무비 DTO 중 금융정보단말기 계약과 엔티티 변환을 분리한 기반 타입입니다. */
public class CostTerminalDto {

    /** 금융정보단말기 정보 DTO. {@link CostDto.TerminalDto} 이름으로 공개됩니다. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(
            name = "CostDto.TerminalDto",
            description = "금융정보단말기 정보",
            requiredProperties = {
                "tmnMngNo",
                "sno",
                "spfTmnNm",
                "tmnKdTc",
                "nsfUsgCone",
                "tmnClsfC",
                "termRqmBgAmt",
                "fcAmt",
                "curC",
                "xcr",
                "xcrBseDt",
                "dfrCleC",
                "indRsn",
                "cgprId",
                "cgprNm",
                "tmnClsfCNm",
                "tmnKdTcNm",
                "dfrCleCNm",
                "termSvnTemC",
                "termSvnTemNm",
                "termSvnDpmC",
                "termSvnDpmNm",
                "rmk"
            })
    public static class TerminalDto {
        @Schema(description = "단말기관리번호", example = "TER_2026_0001", nullable = true)
        private String tmnMngNo;

        @Schema(description = "단말기일련번호", example = "1", nullable = true)
        private Integer sno;

        @Schema(description = "단말기명", example = "대면업무용 단말기")
        private String spfTmnNm;

        @Schema(description = "단말기이용방법", example = "본회선 활용")
        private String tmnKdTc;

        @Schema(description = "단말기용도", example = "창구업무 및 대민지원")
        private String nsfUsgCone;

        @Schema(description = "단말기서비스", example = "인터넷/금융 전용망")
        private String tmnClsfC;

        @Schema(description = "단말기금액", example = "1500000")
        private BigDecimal termRqmBgAmt;

        @Schema(description = "외화금액 (외화 원금. 원화 행은 null)", example = "1000", nullable = true)
        private BigDecimal fcAmt;

        @Schema(description = "통화", example = "KRW")
        private String curC;

        @Schema(description = "환율", example = "1", nullable = true)
        private BigDecimal xcr;

        @Schema(description = "환율기준일자", example = "2026-04-03", nullable = true)
        private String xcrBseDt;

        @Schema(description = "지급주기", example = "매월")
        private String dfrCleC;

        @Schema(description = "증감사유", example = "노후 교체에 따른 한시적 인상")
        private String indRsn;

        @Schema(description = "담당자", example = "홍길동")
        private String cgprId;

        @Schema(description = "담당자명", nullable = true)
        private String cgprNm;

        @Schema(description = "단말기서비스명(단말기종류)", nullable = true)
        private String tmnClsfCNm;

        @Schema(description = "단말기이용방법명", nullable = true)
        private String tmnKdTcNm;

        @Schema(description = "지급주기명", nullable = true)
        private String dfrCleCNm;

        @Schema(description = "담당팀", example = "00101")
        private String termSvnTemC;

        @Schema(description = "담당팀명 스냅샷", nullable = true)
        private String termSvnTemNm;

        @Schema(description = "담당부서", example = "001")
        private String termSvnDpmC;

        @Schema(description = "담당부서명 스냅샷", nullable = true)
        private String termSvnDpmNm;

        @Schema(description = "비고", example = "특이사항 없음")
        private String rmk;

        /** DTO → Entity 변환 (termBgNo, termBgSno는 서비스에서 설정) */
        public Btermm toEntity() {
            return Btermm.builder()
                    .tmnMngNo(tmnMngNo)
                    .sno(sno)
                    .spfTmnNm(spfTmnNm)
                    .tmnKdTc(tmnKdTc)
                    .nsfUsgCone(nsfUsgCone)
                    .tmnClsfC(tmnClsfC)
                    .termRqmBgAmt(termRqmBgAmt)
                    .curC(curC)
                    .xcr(xcr)
                    .xcrBseDt(DateFormatUtil.toYmd8(xcrBseDt))
                    .dfrCleC(CodeDefaults.orNotApplicable(dfrCleC))
                    .indRsn(indRsn)
                    .cgprId(cgprId)
                    .termSvnTemC(termSvnTemC)
                    .termSvnDpmC(termSvnDpmC)
                    .rmk(rmk)
                    .fcAmt(fcAmt)
                    .delYn("N")
                    .build();
        }

        /** Entity → DTO 변환 */
        public static TerminalDto fromEntity(Btermm entity) {
            return TerminalDto.builder()
                    .tmnMngNo(entity.getTmnMngNo())
                    .sno(entity.getSno())
                    .spfTmnNm(entity.getSpfTmnNm())
                    .tmnKdTc(entity.getTmnKdTc())
                    .nsfUsgCone(entity.getNsfUsgCone())
                    .tmnClsfC(entity.getTmnClsfC())
                    .termRqmBgAmt(entity.getTermRqmBgAmt())
                    .curC(entity.getCurC())
                    .xcr(entity.getXcr())
                    .xcrBseDt(entity.getXcrBseDt())
                    .dfrCleC(entity.getDfrCleC())
                    .indRsn(entity.getIndRsn())
                    .cgprId(entity.getCgprId())
                    .cgprNm(entity.getCgprNm()) // 담당자명 스냅샷 (행번 미해석 시 표시 폴백)
                    .termSvnTemC(entity.getTermSvnTemC())
                    .termSvnTemNm(entity.getSvnTemNm())
                    .termSvnDpmC(entity.getTermSvnDpmC())
                    .termSvnDpmNm(entity.getSvnDpmNm())
                    .rmk(entity.getRmk())
                    .fcAmt(entity.getFcAmt())
                    .build();
        }
    }
}
