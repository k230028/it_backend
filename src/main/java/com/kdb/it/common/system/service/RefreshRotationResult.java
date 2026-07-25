package com.kdb.it.common.system.service;

/**
 * Refresh Token 정상 회전 결과.
 *
 * <p>{@link RefreshTokenRotator#rotate(String)}가 재사용·만료 등 이상 없이 정상 회전을 완료했을 때 반환하는 값 객체다.
 * 오케스트레이터(향후 {@code AuthService})가 이 값으로 {@code AuthDto.RefreshResponse}를 구성해 컨트롤러에 반환한다.
 *
 * @param accessToken 새로 발급된 Access Token
 * @param refreshToken 회전된(신규) Refresh Token 원문 — 컨트롤러가 httpOnly 쿠키로 재설정한다
 * @param eno 토큰 소유자 사번 — 오케스트레이터의 로깅·후속 처리에 사용
 */
public record RefreshRotationResult(String accessToken, String refreshToken, String eno) {}
