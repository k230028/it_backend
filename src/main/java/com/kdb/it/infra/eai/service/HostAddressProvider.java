package com.kdb.it.infra.eai.service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 표준전문 시스템공통부의 IP/MAC 주소 공급 시임.
 *
 * <p>운영에선 로컬 호스트에서 조회하고, 테스트에선 고정값 구현으로 대체하여 전문 바이트를 결정적으로 만든다. 반환값은 패딩 전 원시 문자열이다.
 */
public interface HostAddressProvider {

    /** 패딩 전 IP 주소(예: "10.1.2.3"). 조회 실패 시 빈 문자열. */
    String ipAddress();

    /** 패딩 전 MAC 주소 hex(구분자 없음, 예: "001122334455"). 조회 실패 시 빈 문자열. */
    String macAddress();

    /** 운영용 기본 구현 — {@link InetAddress}/{@link NetworkInterface} 기반. */
    @Slf4j
    @Component
    class LocalHostAddressProvider implements HostAddressProvider {

        @Override
        public String ipAddress() {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                log.warn("EAI ipAddress 조회 실패 — 전문 공통부 IP 공백 처리", e);
                return "";
            }
        }

        @Override
        public String macAddress() {
            try {
                InetAddress local = InetAddress.getLocalHost();
                NetworkInterface ni = NetworkInterface.getByInetAddress(local);
                if (ni == null) {
                    return "";
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac == null) {
                    return "";
                }
                StringBuilder sb = new StringBuilder();
                for (byte b : mac) {
                    sb.append(String.format("%02X", b));
                }
                return sb.toString();
            } catch (UnknownHostException | SocketException e) {
                log.warn("EAI macAddress 조회 실패 — 전문 공통부 MAC 공백 처리", e);
                return "";
            }
        }
    }
}
