package com.kdb.it.infra.eai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * HostAddressProvider 인터페이스 및 중첩 구현 클래스
 * {@link HostAddressProvider.LocalHostAddressProvider} 커버리지.
 *
 * <p>Coverage=0% 상태인 LocalHostAddressProvider의 두 public 메서드(ipAddress/macAddress)와
 * 인터페이스 계약을 테스트합니다. 특정 머신 IP에 의존하지 않도록 형식/비-null 단언만 사용합니다.</p>
 */
@DisplayName("HostAddressProvider 구현체 테스트")
class HostAddressProviderTest {

    // -----------------------------------------------------------------------
    // LocalHostAddressProvider — ipAddress()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("LocalHostAddressProvider.ipAddress()")
    class IpAddress {

        @Test
        @DisplayName("로컬호스트 IP를 반환하거나 조회 실패 시 빈 문자열을 반환한다")
        void returns_nonNull_string() {
            // Arrange
            HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();

            // Act
            String ip = provider.ipAddress();

            // Assert — null이 아니어야 함 (성공 또는 빈 문자열)
            assertThat(ip).isNotNull();
        }

        @Test
        @DisplayName("반환 값은 IP 형식이거나 빈 문자열")
        void returns_ip_format_or_empty() {
            HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();
            String ip = provider.ipAddress();

            // 빈 문자열(조회 실패) 또는 점(.) 포함 IPv4 / 콜론(:) 포함 IPv6
            if (!ip.isEmpty()) {
                assertThat(ip).matches(".*[.:].*");
            }
        }
    }

    // -----------------------------------------------------------------------
    // LocalHostAddressProvider — macAddress()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("LocalHostAddressProvider.macAddress()")
    class MacAddress {

        @Test
        @DisplayName("MAC 주소를 반환하거나 조회 실패 시 빈 문자열을 반환한다")
        void returns_nonNull_string() {
            // Arrange
            HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();

            // Act
            String mac = provider.macAddress();

            // Assert
            assertThat(mac).isNotNull();
        }

        @Test
        @DisplayName("비어있지 않으면 16진수 대문자 문자열이다")
        void returns_hex_uppercase_or_empty() {
            HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();
            String mac = provider.macAddress();

            if (!mac.isEmpty()) {
                // 구분자 없는 대문자 16진수
                assertThat(mac).matches("[0-9A-F]+");
            }
        }

        @Test
        @DisplayName("비어있지 않으면 12자리 이상이다 (6바이트 × 2 hex)")
        void nonempty_mac_is_at_least_12_chars() {
            HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();
            String mac = provider.macAddress();

            if (!mac.isEmpty()) {
                assertThat(mac.length()).isGreaterThanOrEqualTo(12);
            }
        }
    }

    // -----------------------------------------------------------------------
    // 인터페이스 계약 — 익명 구현체
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("HostAddressProvider 인터페이스 계약")
    class InterfaceContract {

        @Test
        @DisplayName("익명 구현체로 고정 IP/MAC 반환 가능 (테스트 시임 패턴)")
        void anonymousImpl_fixedValues() {
            // Arrange — 테스트에서 사용하는 고정값 시임 패턴
            HostAddressProvider stub = new HostAddressProvider() {
                @Override public String ipAddress() { return "10.0.0.1"; }
                @Override public String macAddress() { return "001122334455"; }
            };

            // Assert
            assertThat(stub.ipAddress()).isEqualTo("10.0.0.1");
            assertThat(stub.macAddress()).isEqualTo("001122334455");
        }

        @Test
        @DisplayName("LocalHostAddressProvider는 HostAddressProvider를 구현")
        void localHost_implements_interface() {
            HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();
            assertThat(provider).isInstanceOf(HostAddressProvider.class);
        }
    }

    // -----------------------------------------------------------------------
    // 반복 호출 안정성
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("동일 인스턴스에서 ipAddress()를 두 번 호출해도 일관된 결과")
    void ipAddress_consistent_across_calls() {
        HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();
        String first = provider.ipAddress();
        String second = provider.ipAddress();
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("동일 인스턴스에서 macAddress()를 두 번 호출해도 일관된 결과")
    void macAddress_consistent_across_calls() {
        HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();
        String first = provider.macAddress();
        String second = provider.macAddress();
        assertThat(first).isEqualTo(second);
    }

    // -----------------------------------------------------------------------
    // 조회 실패 분기 — 정적 메서드 모킹으로 결정적으로 예외/널 경로 커버
    // (머신 네트워크 상태에 의존하지 않도록 InetAddress/NetworkInterface 정적 모킹)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("LocalHostAddressProvider 조회 실패 분기")
    class FailureBranches {

        @Test
        @DisplayName("getLocalHost가 UnknownHostException이면 ipAddress()는 빈 문자열을 반환한다")
        void ipAddress_returnsEmpty_onUnknownHost() {
            // Arrange — InetAddress.getLocalHost()가 예외를 던지도록 정적 모킹
            try (MockedStatic<InetAddress> ia = mockStatic(InetAddress.class)) {
                ia.when(InetAddress::getLocalHost).thenThrow(new UnknownHostException("호스트 조회 실패"));
                HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();

                // Act
                String ip = provider.ipAddress();

                // Assert — catch 분기 진입 → 빈 문자열
                assertThat(ip).isEmpty();
            }
        }

        @Test
        @DisplayName("getLocalHost가 UnknownHostException이면 macAddress()는 빈 문자열을 반환한다")
        void macAddress_returnsEmpty_onUnknownHost() {
            // Arrange
            try (MockedStatic<InetAddress> ia = mockStatic(InetAddress.class)) {
                ia.when(InetAddress::getLocalHost).thenThrow(new UnknownHostException("호스트 조회 실패"));
                HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();

                // Act
                String mac = provider.macAddress();

                // Assert — catch 분기 진입 → 빈 문자열
                assertThat(mac).isEmpty();
            }
        }

        @Test
        @DisplayName("NetworkInterface 조회 결과가 null이면 macAddress()는 빈 문자열을 반환한다")
        void macAddress_returnsEmpty_whenNetworkInterfaceNull() throws Exception {
            // Arrange — 실제 루프백 주소를 모킹 전에 확보 후, getByInetAddress가 null을 반환하도록 정적 모킹
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            try (MockedStatic<InetAddress> ia = mockStatic(InetAddress.class);
                 MockedStatic<NetworkInterface> ni = mockStatic(NetworkInterface.class)) {
                ia.when(InetAddress::getLocalHost).thenReturn(loopback);
                ni.when(() -> NetworkInterface.getByInetAddress(any())).thenReturn(null);
                HostAddressProvider provider = new HostAddressProvider.LocalHostAddressProvider();

                // Act
                String mac = provider.macAddress();

                // Assert — ni == null 분기 진입 → 빈 문자열
                assertThat(mac).isEmpty();
            }
        }
    }
}
