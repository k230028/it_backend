package com.kdb.it.domain.migration.service.adapter;

/**
 * 편성률 적용 의도입니다.
 *
 * <p>사업·전산업무비는 채번 후에야 관리번호를 알 수 있으므로 {@code naturalKeyOrPk}에 자연키(전산업무비) 또는 정규화 사업명(사업)을 담고, {@code
 * MigrationImportService}가 채번 결과로 실제 PK로 치환한 뒤 {@code BudgetWorkDto.ItemRate}를 만듭니다.
 *
 * @param orcTb 원본 테이블 — 접두어 없는 {@code BPROJM} 또는 {@code BCOSTM}
 * @param naturalKeyOrPk 전산업무비 자연키 또는 정규화 사업명
 * @param percent 편성률 (0~100)
 */
public record RateIntent(String orcTb, String naturalKeyOrPk, int percent) {}
