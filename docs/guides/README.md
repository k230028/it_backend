# 백엔드 개발 가이드

필수 작업 계약은 [백엔드 CLAUDE](../../CLAUDE.md), 설치·실행·배포는 [백엔드 README](../../README.md)가 담당합니다. 이 디렉터리는 반복해서 참고하는 구현 방법과 설계 배경만 보관합니다.

## 아키텍처와 규약

- [레이어와 패키지](architecture/layering-and-packages.md)
- [한글 주석 스타일](conventions/comment-style.md)

## 영속성과 데이터 모델

- [데이터 모델 인덱스](persistence/data-model.md)
- [컬럼 명명과 충돌 규칙](persistence/column-naming.md)
- [엔티티와 감사 로그](persistence/entities-and-audit.md)
- [QueryDSL과 Oracle](persistence/querydsl-and-oracle.md)

물리 DDL·DML과 마이그레이션 작성 규칙은 [데이터베이스 가이드](../../../it_database/docs/guides/README.md)를 따릅니다.

## 인증·인가·파일 보안

- [인증과 인가](security/authentication-authorization.md)
- [데이터 접근 범위](security/data-scope.md)
- [파일 보안](security/file-security.md)

특정 마이그레이션의 적용·복구 기록은 `docs/operations/`에 둡니다.

## 도메인

- [정보화사업 집행](domains/project-execution.md)
- [편성요청서 반입](domains/request-form-import.md)
- [Tiptap 변수](domains/tiptap-variables.md)

## 외부 연동

- [EAI](integrations/eai.md)
- [알림](integrations/notifications.md)
- [SSO](integrations/sso.md)

## 운영

- [Flyway](operations/flyway.md)
- [로깅](operations/logging.md)
- [실시간 로그](operations/realtime-logs.md)
- [감사로그 저장 실패 모니터링](operations/audit-failure-monitoring.md)

## 작성 원칙

- 가이드에는 재사용 가능한 규칙·예제·체크리스트만 기록합니다.
- 날짜, 적용 환경, 배포 상태, 복구 결과는 `../operations/`에 기록합니다.
- 파일·테스트 개수처럼 자주 변하는 수치를 넣지 않습니다.
- CLAUDE와 README의 본문을 복제하지 말고 정식 문서로 연결합니다.
