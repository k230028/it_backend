# JDK 25 개발 환경 통일 설계

## 목표

백엔드의 Gradle toolchain, Eclipse JDT compiler 및 개발 PC의 Gradle 실행 JDK를 25로 통일한다.

## 현황과 원인

`build.gradle`과 `.settings/org.eclipse.jdt.core.prefs`는 Java 25를 사용하지만,
`.settings/org.eclipse.buildship.core.prefs`에는 특정 개발 PC의 JDK 17 절대 경로가 저장되어 있다.
이 설정은 다른 개발 PC로 전달될 수 없으며 프로젝트 설정 간 JDK 불일치를 만든다.

## 설계

- `.settings/org.eclipse.buildship.core.prefs`의 `java.home` 항목을 제거한다.
- Gradle 실행 JDK는 각 개발 PC의 `JAVA_HOME`에서 가져온다.
- 각 개발 PC는 `JAVA_HOME`과 `PATH`가 동일한 JDK 25 설치를 가리키도록 구성한다.
- Java 버전의 프로젝트 기준은 기존 `build.gradle`의 Java 25 toolchain과 JDT Java 25 설정으로 유지한다.
- 사용자명, VS Code 확장 캐시 및 로컬 Gradle 설치 경로 등 기존 PC 종속 설정은 이번 변경 범위에서 다루지 않는다.

## 검증

1. `java -version`이 Java 25를 보고하는지 확인한다.
2. `gradlew -version`의 Launcher JVM과 Daemon JVM이 Java 25인지 확인한다.
3. `gradlew compileJava compileTestJava`를 실행해 Java 25 기준 컴파일이 성공하는지 확인한다.

구성 파일 변경이므로 별도의 단위 테스트는 추가하지 않고 실제 Gradle 실행과 컴파일로 검증한다.
