package com.kdb.it.common.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * 클라이언트 IP 추출 유틸 — 멀티 IP 분리 + 신뢰 프록시 게이트.
 *
 * <p>{@code X-Forwarded-For}는 임의 위조가 가능하므로, 직접 연결한 프록시 ({@link
 * HttpServletRequest#getRemoteAddr()})가 신뢰 allowlist에 포함된 경우에만 헤더를 신뢰합니다. 멀티 IP({@code "client,
 * proxy1, proxy2"})는 최좌측(원 클라이언트)만 사용합니다.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    /**
     * 신뢰 프록시 게이트를 적용해 클라이언트 IP를 추출합니다.
     *
     * @param request HTTP 요청
     * @param trustedProxies 신뢰하는 직접 연결 프록시 IP 집합. 비어 있으면 XFF를 신뢰하지 않음.
     * @return 원 클라이언트 IP(신뢰 시 XFF 최좌측, 아니면 remoteAddr)
     */
    public static String resolve(HttpServletRequest request, Set<String> trustedProxies) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies != null && trustedProxies.contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
                String first = xff.split(",")[0].trim(); // 최좌측(원 클라이언트)
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        return remoteAddr;
    }
}
