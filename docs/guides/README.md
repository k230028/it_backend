# 백엔드 가이드

필수 규칙은 프로젝트 루트 `CLAUDE.md`가 담당하고, 이 디렉터리는 주제별 설계와 구현 배경을 설명합니다.

## 아키텍처

- [레이어와 패키지](architecture/layering-and-packages.md)

## 영속성

- [데이터 모델 인덱스](persistence/data-model.md)
- [엔티티와 감사 로그](persistence/entities-and-audit.md)
- [QueryDSL과 Oracle](persistence/querydsl-and-oracle.md)
- [컬럼 명명](persistence/column-naming.md)

## 보안

- [인증과 인가](security/authentication-authorization.md)
- [데이터 접근 범위](security/data-scope.md)
- [파일 보안](security/file-security.md)

## 도메인·연동·운영

- [사업 집행](domains/project-execution.md)
- [Tiptap 변수](domains/tiptap-variables.md)
- [알림](integrations/notifications.md)
- [EAI](integrations/eai.md)
- [SSO](integrations/sso.md)
- [Flyway](operations/flyway.md)
- [로깅](operations/logging.md)
- [실시간 로그](operations/realtime-logs.md)
- [주석 스타일](conventions/comment-style.md)

변경 날짜와 파일·테스트 개수는 가이드에 기록하지 않습니다. 미구현 사항은 루트 `TASK.md`에서 관리합니다.
