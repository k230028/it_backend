package com.kdb.it.common.admin.waslog.appender;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Context;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 적재 측과 조회 측이 서로 다른 클래스로더에 로드돼도 같은 로그를 본다는 회귀 검증.
 *
 * <p>실제로 벌어진 장애다. logback은 appender 클래스를 logback 자신을 로드한 클래스로더로 찾으므로 애플리케이션 클래스로더가 {@link
 * RingBufferAppender}를 로드하는데, {@code spring-boot-devtools}가 붙은 IDE 기동에서는 조회 측(서비스 빈)이 {@code
 * RestartClassLoader}로 다시 로드된다. 버퍼를 {@code static} 필드에 두면 두 쪽이 서로 다른 빈 버퍼를 보게 되고, 화면은 오류 하나 없이 "로그
 * 0건"만 보여준다.
 *
 * <p>여기서는 waslog 패키지만 자식 우선으로 다시 로드하는 클래스로더로 그 상황을 재현한다.
 */
class WasLogBufferClassLoaderIsolationTest {

    /** waslog 패키지만 새로 로드하고 나머지(logback·JDK)는 부모에 위임하는 로더 — devtools RestartClassLoader와 같은 구조. */
    private static final class IsolatedLoader extends URLClassLoader {

        private IsolatedLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.kdb.it.common.admin.waslog")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    @Test
    @DisplayName("다른 클래스로더로 로드된 버퍼도 같은 logback Context의 적재분을 읽는다")
    void 서로다른클래스로더_같은적재분조회() throws Exception {
        LoggerContext context = new LoggerContext();
        WasLogBuffer.attachedTo(context).add(1L, "ERROR", "main", "com.kdb.it.A", "터짐", "stack");

        URL classes = WasLogBuffer.class.getProtectionDomain().getCodeSource().getLocation();
        // 부모는 애플리케이션 클래스로더 — logback·slf4j는 부모에서 온 같은 클래스여야 한다.
        // waslog 패키지만 위 override가 자식 우선으로 가로챈다.
        try (IsolatedLoader loader =
                new IsolatedLoader(new URL[] {classes}, WasLogBuffer.class.getClassLoader())) {
            Class<?> isolated =
                    loader.loadClass("com.kdb.it.common.admin.waslog.appender.WasLogBuffer");
            // 전제 확인 — 정말 다른 클래스여야 이 테스트가 의미를 갖는다.
            assertThat(isolated).isNotSameAs(WasLogBuffer.class);

            Object buffer = isolated.getMethod("attachedTo", Context.class).invoke(null, context);
            Object snapshot = isolated.getMethod("snapshot").invoke(buffer);
            List<?> entries = (List<?>) snapshot.getClass().getMethod("entries").invoke(snapshot);

            assertThat(entries).hasSize(1);
            Object entry = entries.get(0);
            assertThat(entry.getClass().getMethod("message").invoke(entry)).isEqualTo("터짐");
            assertThat(entry.getClass().getMethod("level").invoke(entry)).isEqualTo("ERROR");
        }
    }
}
