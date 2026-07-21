package com.kdb.it.infra.eai.dto;

/**
 * EAI 표준전문 개별부(param07)의 채널별 입력 데이터.
 *
 * <p>sealed — 신규 채널 추가 시 permits에 등록하고 대응 {@code EaiPayloadSection}을 추가한다.
 */
public sealed interface EaiPayload permits UmsPayload, GwePayload {}
