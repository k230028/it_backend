package com.kdb.it.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.ConsoleAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 콘솔 로그 인코딩 회귀 테스트.
 *
 * <p>{@code logback-spring.xml}의 콘솔 appender는 charset을 {@code ${stdout.encoding:-UTF-8}}로
 * 지정해, JVM이 감지한 콘솔 인코딩(JDK 18+: Windows=MS949, Linux=UTF-8)에 자동 정합된다.
 * 고정 UTF-8이면 Windows 한글 콘솔에서 한글이 깨진다(예: "인증서버" → "?몄쬆?쒕쾭").</p>
 *
 * <p>본 테스트는 동일한 charset 표현식을 plain logback 설정(springProfile 미사용)으로 로드해,
 * logback이 {@code stdout.encoding} 시스템 속성을 실제 {@link Charset}으로 치환하는지(이 수정의 핵심)
 * 검증한다. {@code springProfile} 태그는 Spring 확장이 필요해 단독 Joran 로드가 불가하므로 제외한다.</p>
 */
class LogbackConsoleCharsetTest {

    @Test
    @DisplayName("콘솔 appender charset은 stdout.encoding(미설정 시 UTF-8)으로 해석된다")
    void consoleCharset_resolvesFromStdoutEncoding() throws Exception {
        // logback-spring.xml과 동일한 charset 표현식을 사용하는 최소 설정
        String config = """
                <configuration>
                  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder>
                      <pattern>%msg%n</pattern>
                      <charset>${stdout.encoding:-UTF-8}</charset>
                    </encoder>
                  </appender>
                  <root level="INFO"><appender-ref ref="CONSOLE"/></root>
                </configuration>
                """;

        LoggerContext context = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));

        ConsoleAppender<?> console = (ConsoleAppender<?>) context.getLogger("ROOT").getAppender("CONSOLE");
        Charset actual = ((PatternLayoutEncoder) console.getEncoder()).getCharset();

        // 기대값: stdout.encoding이 설정·지원되면 그 charset, 아니면 폴백 UTF-8
        String stdoutEncoding = System.getProperty("stdout.encoding");
        Charset expected = (stdoutEncoding != null && Charset.isSupported(stdoutEncoding))
                ? Charset.forName(stdoutEncoding)
                : StandardCharsets.UTF_8;

        assertThat(actual).isEqualTo(expected);
        // 고정 UTF-8 회귀 방지: stdout.encoding이 비-UTF-8(예: MS949)이면 charset도 그것을 따라야 함
        if (stdoutEncoding != null && Charset.isSupported(stdoutEncoding)) {
            assertThat(actual).isEqualTo(Charset.forName(stdoutEncoding));
        }

        context.stop();
    }
}
