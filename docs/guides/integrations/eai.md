# EAI 가이드

`EaiService`는 KDB 표준전문을 조립하고 사내 EAI 게이트웨이로 전송합니다.

- 채널별 payload는 `EaiPayload`와 `EaiPayloadSection` 구현으로 분리합니다.
- `eai.enabled=false`에서는 전문 검증까지만 수행하고 HTTP를 호출하지 않습니다.
- 성공·실패·스킵은 `EaiResult`로 표현하고 예외로 원 업무를 실패시키지 않습니다.
- 휴대폰, OTP, 토큰과 전문 전체를 평문 로그로 남기지 않습니다.
- 운영 인터페이스 ID와 템플릿 값은 환경 설정과 운영 승인값을 사용합니다.

새 채널은 payload record와 section 구현을 추가하고 조립·마스킹·비활성 모드 테스트를 작성합니다.
