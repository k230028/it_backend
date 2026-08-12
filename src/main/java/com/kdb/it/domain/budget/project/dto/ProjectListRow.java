package com.kdb.it.domain.budget.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 정보화사업 목록 경량 프로젝션 DTO(#7).
 *
 * <p>목록 화면에 필요한 식별/요약 컬럼만 담으며, 1000자+ 대용량 텍스트 (사업설명/현황/기대효과/문제/추진경과/고객유형 등)는 select하지 않는다. 상세는 기존
 * 엔티티 조회 경로를 유지한다.
 *
 * @param abusMngNo 사업관리번호 (프로젝트관리번호, 예: "PRJ-2026-0001")
 * @param sno 프로젝트 순번
 * @param abusNm 사업명
 * @param bzTpC 사업유형명 (물리컬럼 ABUS_PPO_CONE, 공통코드 ABUS_PPO 코드값명 저장)
 * @param svnDpmC 주관부서코드
 * @param dvmDpmC 개발부서코드 (IT부서)
 * @param sttDtm 시작일자 (사업 개시 예정일)
 * @param endDtm 종료일자 (사업 완료 예정일)
 * @param bseYy 기준연도 (예산연도, YYYY)
 * @param odnYn 경상여부 ('Y'=경상사업, null 또는 'N'=일반 정보화사업)
 * @param abusTc 사업구분코드 (신규/계속 여부)
 * @param rprStsTc 보고상태구분코드 (공통코드 2자리)
 * @param delYn 삭제여부 ('Y'=삭제)
 */
@Schema(name = "ProjectListRow")
public record ProjectListRow(
        String abusMngNo,
        Integer sno,
        String abusNm,
        String bzTpC,
        String svnDpmC,
        String dvmDpmC,
        LocalDate sttDtm,
        LocalDate endDtm,
        String bseYy,
        String odnYn,
        String abusTc,
        String rprStsTc,
        String delYn) {}
