package com.kdb.it.domain.budget.project.dto;

import com.kdb.it.domain.budget.project.entity.Bprojm;

/**
 * 정보화사업 엔티티 → 조회 응답 DTO 변환기.
 *
 * <p>{@link Bprojm} 엔티티의 컬럼값을 {@link ProjectDto.Response}로 복사하는 책임만 갖습니다. DTO 정의(필드·Swagger 스키마)와
 * 엔티티 매핑 책임을 분리하기 위해 {@code ProjectDto.Response}에서 떼어냈습니다.
 *
 * <p>인스턴스화하지 않는 유틸리티 클래스입니다.
 */
public final class ProjectResponseMapper {

    private ProjectResponseMapper() {}

    /**
     * {@link Bprojm} 엔티티를 조회 응답 DTO로 변환합니다.
     *
     * <p>엔티티의 모든 필드를 DTO로 복사합니다. 연결된 품목 목록, 조직명, 예산 요약 정보 등 엔티티에 없는 파생값은 서비스 계층에서 추가로 설정해야 합니다.
     *
     * @param project 변환할 Bprojm 엔티티 (null 불가)
     * @return 엔티티 컬럼값만 채워진 응답 DTO
     * @throws NullPointerException project가 null인 경우
     */
    public static ProjectDto.Response fromEntity(Bprojm project) {
        return ProjectDto.Response.builder()
                .abusMngNo(project.getAbusMngNo()) // 프로젝트관리번호
                .sno(project.getSno()) // 프로젝트순번
                .abusNm(project.getAbusNm()) // 프로젝트명
                .bzTpC(project.getBzTpC()) // 프로젝트유형
                .svnDpmC(project.getSvnDpmC()) // 주관부서
                .dvmDpmC(project.getDvmDpmC()) // IT부서
                .sttDtm(project.getSttDtm()) // 시작일자
                .endDtm(project.getEndDtm()) // 종료일자
                .prlmHrkOgzCCone(project.getPrlmHrkOgzCCone()) // 주관본부/부문
                .usid(project.getUsid()) // 주관부서담당자
                .dvmUsid(project.getDvmUsid()) // IT부서담당자
                .tlrUsid(project.getTlrUsid()) // 주관부서담당팀장
                .dvmTlrUsid(project.getDvmTlrUsid()) // IT부서담당팀장
                .edrtTc(project.getEdrtTc()) // 전결권
                .abusCone(project.getAbusCone()) // 사업설명
                .cpnSafCone(project.getCpnSafCone()) // 현황
                .abusNcsCone(project.getAbusNcsCone()) // 필요성
                .dgogPpoCone(project.getDgogPpoCone()) // 기대효과
                .plmDes(project.getPlmDes()) // 문제
                .abusRngCone(project.getAbusRngCone()) // 사업범위
                .mnPrgCone(project.getMnPrgCone()) // 추진경과
                .hrfPlnCone(project.getHrfPlnCone()) // 향후계획
                .bzDttNm(project.getBzDttNm()) // 업무구분
                .sklTpTc(project.getSklTpTc()) // 기술유형
                .cstTpTc(project.getCstTpTc()) // 주요사용자
                .dplYn(project.getDplYn()) // 중복여부
                .flfFsgDt(project.getFlfFsgDt()) // 의무완료기한
                .rprStsTc(project.getRprStsTc()) // 보고상태
                .exePttYn(project.getExePttYn()) // 프로젝트추진가능성
                .delYn(project.getDelYn()) // 삭제여부
                .bseYy(project.getBseYy()) // 사업연도
                .odnYn(project.getOdnYn()) // 경상여부
                .abusTc(project.getAbusTc()) // 사업구분
                .cncdRfrNo(project.getCncdRfrNo()) // 관련프로젝트관리번호
                .dfrAmt(project.getDfrAmt()) // 기 지급예산
                .fstEnrDtm(project.getFstEnrDtm()) // 최초 등록 일시
                .fstEnrUsid(project.getFstEnrUsid()) // 최초 등록자
                .lstChgDtm(project.getLstChgDtm()) // 마지막 수정 일시
                .lstChgUsid(project.getLstChgUsid()) // 마지막 수정자
                .build();
    }
}
