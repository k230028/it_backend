package com.kdb.it.domain.payment.repository;

import com.kdb.it.domain.payment.entity.Bpaymm;

/**
 * 대금지급 현재 유효 마스터 + 대상명 단일 조회 결과.
 *
 * <p>{@code get(docNo)}에서 마스터 조회와 대상명 해석을 1개 쿼리로 합치기 위한 행 레코드입니다.
 * 회차별 지급 명세(Bpaymt)는 1:N 별도 데이터이므로 본 행에 포함하지 않고 서비스에서 별도 조회합니다.
 * 대상명({@code targetName})은 대상구분(ioeC)에 따라 Bprojm 또는 Bcostm에서 LEFT JOIN으로
 * 해석되며, 해당 레코드가 없으면 null입니다.</p>
 *
 * @param entity     현재 유효 대금지급 마스터 (lstYn='Y', delYn='N')
 * @param targetName 대상 명칭 (사업=ABUS_NM, 전산업무비=CTT_NM, 없으면 null)
 */
public record PaymentTargetRow(Bpaymm entity, String targetName) {}
