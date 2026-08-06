package com.kdb.it.domain.council.service;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.dto.CouncilProjectRow;
import com.kdb.it.domain.council.entity.Basctm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 협의회 목록 응답 DTO 조립기
 *
 * <p>{@link CouncilService}에서 순수 DTO 조립만 분리한 유틸리티입니다. 리포지토리 조회와 권한 판정은 서비스에 남기고, 여기에는 조회 결과를 {@link
 * CouncilDto.ListResponse}로 옮기는 필드 매핑만 둡니다. 상태를 갖지 않으므로 전부 static이며 인스턴스를 만들지 않습니다.
 */
final class CouncilResponseMapper {

    private CouncilResponseMapper() {}

    /**
     * 협의회 신청대상 DTO 행 → ListResponse 변환 (관리자/일반사용자용)
     *
     * <p>native {@code Object[]} 인덱스 캐스팅은 {@link CouncilProjectRow#fromRow(Object[])} 단일 팩토리(§5.5.4
     * 헬퍼 사용)로 봉인되어 서비스로 새지 않는다. 날짜 타입/문자열 yyyyMMdd 변환은 DTO 생성 시점에 이미 {@code LocalDate}로 끝나 있으므로
     * 여기서는 추가 변환이 없다.
     *
     * @param row native 조회 행. null을 허용하지 않는다
     * @param budgetMap 사업관리번호 → 당해예산(파생) 배치 조회 결과. 키가 없으면 당해예산은 null이 된다
     * @return 목록 응답 DTO
     */
    static CouncilDto.ListResponse toListResponse(
            CouncilProjectRow row, Map<String, BigDecimal> budgetMap) {
        return new CouncilDto.ListResponse(
                row.itPtlAsctId(),
                row.abusMngNo(),
                row.sno(),
                row.abusNm(),
                row.itPtlAsctPrgStsTc(),
                row.itPtlAsctDbrTc(),
                row.cnrcDt(),
                row.cnrcSttTm(),
                row.applied(),
                row.prjYy(),
                row.prjTp(),
                row.svnDpm(),
                // 당해예산은 native 컬럼이 NULL이므로 품목 배치 조회 결과로 파생 산출한다.
                budgetMap.get(row.abusMngNo()),
                row.sttDt(),
                row.endDt(),
                row.itDpm(),
                row.abusCone(),
                row.csfHeldYn(),
                row.hasInfoSecResource());
    }

    /**
     * Basctm 엔티티 → ListResponse 변환 (평가위원용, PRD §16)
     *
     * <p>평가위원 사업카드도 일반사용자/관리자와 동일하게 사업 상세 필드를 채워야 하므로 BPROJM에서
     * prjYy/prjTp/svnDpm/sttDt/endDt/itDpm/prjDes를 함께 매핑합니다. {@code applied}는 이미 신청된 협의회 엔티티에서 만드는
     * 응답이므로 항상 true입니다.
     *
     * @param council 협의회 엔티티. null을 허용하지 않는다
     * @param project BPROJM 사업. 조회되지 않으면 null이며 사업 상세 필드가 전부 null이 된다
     * @param prjNm 목록 제목(사업명 또는 계획명). 해석 규칙은 호출자가 정한다
     * @param prjBg 당해예산(품목 파생값). 없으면 null
     * @param hasInfoSecResource 정보보호 소요자원 보유 여부 — 심의유형 04 노출 조건
     * @return 목록 응답 DTO
     */
    static CouncilDto.ListResponse toListResponse(
            Basctm council,
            Bprojm project,
            String prjNm,
            BigDecimal prjBg,
            boolean hasInfoSecResource) {
        String prjYy = project == null ? null : project.getBseYy();
        String prjTp = project == null ? null : project.getBzTpC();
        String svnDpm = project == null ? null : project.getSvnDpmC();
        LocalDate sttDt = project == null ? null : project.getSttDtm();
        LocalDate endDt = project == null ? null : project.getEndDtm();
        String itDpm = project == null ? null : project.getDvmDpmC();
        String prjDes = project == null ? null : project.getAbusCone();

        return new CouncilDto.ListResponse(
                council.getItPtlAsctId(),
                council.getAbusMngNo(),
                council.getSno(),
                prjNm,
                council.getItPtlAsctPrgStsTc(),
                council.getItPtlAsctDbrTc(),
                council.getCnrcDt(),
                council.getCnrcSttTm(),
                true,
                prjYy,
                prjTp,
                svnDpm,
                prjBg,
                sttDt,
                endDt,
                itDpm,
                prjDes,
                council.getCsfHeldYn(),
                hasInfoSecResource);
    }
}
