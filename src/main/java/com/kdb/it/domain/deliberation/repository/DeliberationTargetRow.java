package com.kdb.it.domain.deliberation.repository;

import com.kdb.it.domain.deliberation.entity.Bdelim;

/**
 * 과업심의 현재 유효 마스터 + 대상명 단일 조회 결과.
 *
 * <p>{@code get(docNo)}에서 마스터 조회와 대상명 해석을 1개 쿼리로 합치기 위한 행 레코드입니다.
 * 대상명({@code targetName})은 대상구분(bgPrnTc)에 따라 Bprojm 또는 Bcostm에서 LEFT JOIN으로
 * 해석되며, 해당 레코드가 없으면 null입니다.</p>
 *
 * @param entity     현재 유효 과업심의 마스터 (lstYn='Y', delYn='N')
 * @param targetName 대상 명칭 (사업=ABUS_NM, 전산업무비=CTT_NM, 없으면 null)
 */
public record DeliberationTargetRow(Bdelim entity, String targetName) {}
