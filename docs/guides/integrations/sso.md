# SSO 가이드

SSO 완료 흐름은 사내 인증 결과를 검증한 뒤 애플리케이션 JWT 쿠키를 발급합니다.

- 직접 사번 입력 우회는 기본 비활성화하고 개발 프로파일에서만 제한적으로 허용합니다.
- `checkauth`(실연동)와 `business`(모의 모드)는 검증에 성공한 사번만 `JwtUtil.generateSsoVerifiedToken`으로 서명해 `sso-verified` httpOnly 쿠키(`Path=/api/auth/sso`, 60초)로 발급합니다. 실패 코드나 빈 사번은 쿠키를 발급하지 않고 수동 로그인으로 폴백합니다.
- `loginProc`/`agentProc`는 상태를 읽거나 바꾸지 않고 `sso-next`/`sso-origin` 쿠키를 쿼리로 옮겨 `complete`로 리다이렉트합니다.
- 완료 단계는 `sso-verified` 토큰의 서명·만료·용도(`tokenUse=sso-verified`)를 검증한 뒤 JWT를 발급하고, 성공·실패와 관계없이 `sso-verified`/`sso-next`/`sso-origin` 쿠키를 삭제합니다.
- 복귀 URL은 허용 Origin 목록에서 선택하여 오픈 리다이렉트를 방지합니다.
- `/sso/**` CORS 예외는 전체 페이지 콜백 전용이며 일반 SPA API에 적용하지 않습니다.
- SSO 통합 로그아웃은 `POST /sso/logout`만 허용합니다. `GET /sso/logout`은 `Allow: POST`와 함께 405를 반환합니다.
- 암호화 토큰 쿼리 값의 `+`는 명시적으로 percent-encoding합니다.
- 외부 JSON 응답은 DTO 또는 `Map<String, Object>`로 받습니다.
- 실제 HTTP 메시지 컨버터와 폼 디코딩을 지나는 통합 테스트를 유지합니다.

운영에서는 `app.sso.allow-direct-eno`와 개발 사용자 전환 기능이 비활성화되어야 하며 `EnvironmentValidator`가 이를 검사합니다.

## 다중 인스턴스 배포 전제

SSO 핸드셰이크는 서버 세션(`HttpSession`)을 사용하지 않습니다. Spring Security는 `STATELESS`이고 인증은 JWT httpOnly 쿠키이며, `checkauth` → `loginProc`/`agentProc` → `complete` 왕복 상태도 모두 쿠키로 운반합니다.

- **로드밸런서 세션 유지(sticky session)가 필요 없습니다.** 왕복의 각 요청이 서로 다른 WAS 인스턴스에 떨어져도 `sso-verified` 토큰은 공유 `jwt.secret`으로 어느 인스턴스든 검증합니다. L4는 Least Connection 같은 순수 부하분산으로 두어도 됩니다.
- 모든 인스턴스가 같은 `jwt.secret`을 주입받아야 합니다. 인스턴스별로 키가 다르면 다른 인스턴스가 발급한 `sso-verified` 토큰을 `JWT 서명·만료·용도 검증에 실패했습니다` 경고와 함께 거부해 수동 로그인으로 떨어집니다.
- 토큰 수명은 `jwt.sso-verified-validity`(기본 60000ms)로 조정합니다. 리다이렉트 왕복만 버티면 되므로 늘릴 이유는 거의 없습니다.
- 서버 저장소가 없으므로 60초 안의 재전송을 서버가 막지는 못합니다. `sso-verified`는 httpOnly·`Path=/api/auth/sso`라 이를 읽을 수 있는 공격자는 Refresh 쿠키도 읽을 수 있어, 위협 모델은 이전 세션 방식과 같습니다. 1회용을 서버가 강제해야 하는 요구가 생기면 MFA와 같은 공유 저장소(`MfaTransactionStore` 패턴)로 옮깁니다.
- `sso-next`/`sso-origin` 쿠키는 복귀 경로를 운반하는 유일한 수단입니다. ESSO 교차 출처 POST 콜백 뒤 마지막 same-site `complete` 내비게이션에 실려 원본 URL을 복원하므로 제거하면 안 됩니다.
