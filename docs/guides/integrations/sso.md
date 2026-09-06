# SSO 가이드

SSO 완료 흐름은 사내 인증 결과를 검증한 뒤 애플리케이션 JWT 쿠키를 발급합니다.

- 직접 사번 입력 우회는 기본 비활성화하고 개발 프로파일에서만 제한적으로 허용합니다.
- `loginProc`/`agentProc`는 Agent 결과(`resultCode`, `resultData`, `secureSessionId`)를 세션 잠금 안에서 한 번만 소비하고 성공한 사번만 완료 단계로 승격합니다.
- 완료 단계는 검증 사번을 한 번만 소비하며 성공·실패와 관계없이 SSO 서버 세션을 무효화합니다.
- 복귀 URL은 허용 Origin 목록에서 선택하여 오픈 리다이렉트를 방지합니다.
- `/sso/**` CORS 예외는 전체 페이지 콜백 전용이며 일반 SPA API에 적용하지 않습니다.
- SSO 통합 로그아웃은 `POST /sso/logout`만 허용합니다. `GET /sso/logout`은 `Allow: POST`와 함께 405를 반환하며 세션 상태를 변경하지 않습니다.
- 암호화 토큰 쿼리 값의 `+`는 명시적으로 percent-encoding합니다.
- 외부 JSON 응답은 DTO 또는 `Map<String, Object>`로 받습니다.
- 실제 HTTP 메시지 컨버터와 폼 디코딩을 지나는 통합 테스트를 유지합니다.

운영에서는 `app.sso.allow-direct-eno`와 개발 사용자 전환 기능이 비활성화되어야 하며 `EnvironmentValidator`가 이를 검사합니다.

## 다중 인스턴스 배포 전제

애플리케이션에서 서버 세션을 쓰는 곳은 이 SSO 핸드셰이크뿐입니다. Spring Security는 `STATELESS`이고 인증은 JWT httpOnly 쿠키이므로, 세션이 필요한 구간은 `checkauth` → `loginProc`/`agentProc` → `complete` 왕복 몇 초가 전부입니다.

- **다중 인스턴스로 배포할 때 로드밸런서의 세션 어피니티(sticky session)가 필수입니다.** 이 구간이 서로 다른 인스턴스로 분산되면 `SsoController`가 세션 결과를 찾지 못해 `SSO 인증 세션이 없습니다.`로 수동 로그인에 떨어집니다. L4 source IP 고정으로 충분합니다 — `checkauth`는 ESSO 서버가 아니라 사용자 브라우저가 리다이렉트로 들어오는 경로라 왕복 전체가 같은 클라이언트 IP입니다.
- 인프라에서 어피니티를 제거하거나 장비 구성을 바꾸면 이 흐름이 조용히 깨지므로, 로드밸런서 변경 시 검토 대상입니다.
- 어피니티에 의존하지 않으려면 세션 상태를 MFA와 같은 공유 저장소(`MfaTransactionStore` 패턴)로 옮겨야 합니다. 현재는 채택하지 않았습니다.
- `sso-next`/`sso-origin` 쿠키는 어피니티와 무관한 별개 방어입니다. ESSO 교차 출처 POST 콜백에는 `SameSite=Lax` 세션 쿠키가 실리지 않아 `checkauth`가 새 세션을 만들며, 이때 복귀 경로를 살리는 유일한 수단입니다. 제거하면 안 됩니다.
