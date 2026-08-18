# 로깅 가이드

`src/main/resources/logback-spring.xml`이 콘솔과 파일 로깅을 구성합니다.

- 모든 프로파일 기본 경로: `/log/springitp`
- 활성 로그와 월 단위 보관 로그를 분리합니다.
- 보관 기간과 전체 용량 상한을 유지합니다.
- 파일 로그는 UTF-8을 사용합니다.
- Windows 콘솔은 `stdout.encoding`을 따라 한글 깨짐을 방지합니다.
- 로그 수준은 프로파일의 `logging.level.*` 설정을 사용합니다.

- EAI 전문 상세 로그는 전용 로거 `com.kdb.it.infra.eai.wire`로 분리합니다(`EAI_WIRE_LOG_LEVEL`). 공통부만 필드 단위로 남기고 개별부는 길이만 남깁니다.

JWT, 비밀번호, DB 비밀값, 휴대폰, OTP, 외부 전문 전체는 로그에 기록하지 않습니다. 실패를 삼키는 부수효과 경로도 식별자와 원인을 진단할 수 있는 warn/error를 남깁니다.
