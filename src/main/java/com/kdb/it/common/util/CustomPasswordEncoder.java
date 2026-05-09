package com.kdb.it.common.util;

import com.kdb.it.exception.CustomGeneralException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;

/**
 * 커스텀 비밀번호 암호화 클래스
 *
 * <p>Spring Security의 {@link PasswordEncoder} 인터페이스를 구현하여
 * SHA-256 해싱 알고리즘 기반의 비밀번호 암호화를 수행합니다.</p>
 *
 * <p>암호화 흐름:</p>
 * <pre>
 *   평문 입력 → SHA-256 해싱 (빈 Salt 적용) → Base64 인코딩 → 암호문 출력
 * </pre>
 *
 * <p>⚠ 주의: 빈 Salt(Empty Salt) 사용으로 인해 Rainbow Table 공격에 취약합니다.
 * 운영 환경에서는 BCrypt 등 Salt가 내장된 알고리즘 사용을 권장합니다.</p>
 */
public class CustomPasswordEncoder implements PasswordEncoder {

    /**
     * 평문 비밀번호를 SHA-256으로 암호화합니다.
     *
     * <p>Spring Security의 {@code DaoAuthenticationProvider}에서
     * 회원가입/비밀번호 저장 시 자동으로 호출됩니다.</p>
     *
     * @param rawPassword 암호화할 평문 비밀번호
     * @return SHA-256 + Base64 인코딩된 암호문 문자열
     */
    @Override
    public String encode(CharSequence rawPassword) {
        return encrypt(rawPassword.toString());
    }

    /**
     * 입력된 평문 비밀번호와 저장된 암호화 비밀번호를 비교합니다.
     *
     * <p>로그인 시 Spring Security가 자동으로 호출합니다.
     * 입력받은 비밀번호를 동일한 SHA-256 방식으로 암호화한 후 DB의 암호문과 비교합니다.</p>
     *
     * @param rawPassword     사용자가 입력한 평문 비밀번호
     * @param encodedPassword DB에 저장된 암호화 비밀번호
     * @return 비밀번호 일치 여부 (true: 일치, false: 불일치)
     */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        // 입력받은 비밀번호를 동일한 방식으로 암호화하여 저장된 암호문과 비교
        String inputEncoded = encrypt(rawPassword.toString());
        return inputEncoded.equals(encodedPassword);
    }

    /**
     * 요청된 SHA-256 암호화 로직
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>SHA-256 MessageDigest 인스턴스 생성</li>
     *   <li>빈 Salt 값을 digest에 업데이트 (현재 미사용)</li>
     *   <li>평문을 UTF-8 바이트로 변환 후 SHA-256 해싱</li>
     *   <li>해싱 결과를 Base64로 인코딩</li>
     * </ol>
     *
     * 평문 입력 → SHA-256 해싱 (빈 Salt 적용) → Base64 인코딩
     *
     * @param plainText 암호화할 평문 문자열
     * @return Base64로 인코딩된 SHA-256 해시 문자열
     * @throws CustomGeneralException SHA-256 알고리즘을 찾을 수 없을 때 발생
     */
    public String encrypt(final String plainText) throws CustomGeneralException {
        try {
            // SHA-256 암호화 알고리즘 인스턴스 생성
            final MessageDigest msg = MessageDigest.getInstance("SHA-256");
            msg.update("".getBytes(StandardCharsets.UTF_8)); // 빈 솔트값 추가 (취약점)
            // Base64 인코더 인스턴스 생성
            final Encoder encoder = Base64.getEncoder();
            // 평문을 UTF-8 바이트 배열로 변환 후 SHA-256 해시 수행
            final byte[] digest = msg.digest(plainText.getBytes(StandardCharsets.UTF_8));
            // 인코딩: SHA-256 해시 바이트 배열을 Base64 문자열로 변환
            return encoder.encodeToString(digest);
        } catch (final NoSuchAlgorithmException e) {
            // JVM에서 SHA-256 알고리즘을 지원하지 않는 경우 (정상적인 JVM에서는 발생하지 않음)
            throw new CustomGeneralException("Fail to encrypt SHA-256", e);
        }
    }
}
