# 인증과 인가 가이드

## JWT와 쿠키

- Access Token은 15분, Refresh Token은 7일입니다.
- 두 토큰은 httpOnly 쿠키로 전달합니다.
- Access Token에는 사번, 자격등급 목록과 부서코드를 포함합니다.
- Refresh Token 갱신 시 사용자 권한과 부서를 DB에서 다시 읽습니다.
- Refresh Token은 회전하며 유예 기간 이후 재사용이 탐지되면 사용자 토큰 패밀리를 폐기합니다.
- Refresh Token 원문은 DB에 저장하지 않습니다. `TPRMPP_CRTOKM.ECY_RNW_PUB_TOK_CONE`에는 조회용 소문자 SHA-256 HEX 값만 저장합니다.
- Access Token이 만료되어도 Refresh 쿠키로 로그아웃하면 해당 토큰 소유자의 패밀리를 폐기합니다.
- 운영에서는 Bearer 헤더 폴백을 비활성화하고 쿠키 인증을 기본으로 합니다.

## 운영 인증 안전장치

`prod` 프로파일에서는 다음 위험 설정을 허용하지 않으며, 하나라도 감지되면 애플리케이션 기동이 실패합니다.

- `sso.mock-enabled=true`
- `app.auth.allow-bearer-header=true`
- `app.cookie.secure=false`

SSO 인증 성공 경계에서는 기존 HTTP 세션 ID를 교체하여 세션 고정을 방지합니다.

## RBAC

| 자격등급   | 역할                 | 의미            |
| ---------- | -------------------- | --------------- |
| `ITPAD001` | `ROLE_ADMIN`         | 시스템 관리자   |
| `ITPAD002` | `ROLE_INFOSEC_ADMIN` | 정보보호 관리자 |
| `ITPZZ002` | `ROLE_DEPT_MANAGER`  | 부서 관리자     |
| `ITPZZ001` | `ROLE_USER`          | 일반 사용자     |

관리자 전용 도메인은 컨트롤러 클래스 수준 `@PreAuthorize("hasRole('ADMIN')")`를 사용합니다. 협의회와 사업 집행처럼 세부 업무 범위가 필요한 API는 서비스 계층에서 역할·소유권·상태를 검증합니다.

## CSRF와 CORS

쿠키 기반 JWT는 Stateless여도 브라우저가 자격증명을 자동 전송하므로 CSRF 검토 대상입니다.

- 현재 경계는 `SameSite=Lax`, 명시적 CORS Origin과 `allowCredentials=true` 조합입니다.
- CORS는 응답 읽기 정책이지 단독 CSRF 방어가 아닙니다.
- `SameSite=None`, 와일드카드 Origin, 임의 Origin 추가는 별도 CSRF 보강 없이 허용하지 않습니다.
- `/sso/**` 전체 페이지 콜백 예외를 일반 SPA API로 확대하지 않습니다.

### 현재 위협 모델

현재 전역 `csrf.disable()`은 아래 경계가 모두 유지되는 동안에만 적용합니다. 각 경로의 자동 전송 자격증명, 상태 변경 여부와 방어 수단을 함께 검토합니다.

| 경로 | 자동 전송 자격증명 | 상태 변경 | 현재 방어 | 잔여 위험 |
| --- | --- | --- | --- | --- |
| `POST /api/auth/login` | 기존 JWT 불필요 | Access/Refresh 발급 | 명시 CORS, 로그인 검증·잠금 | 로그인 CSRF는 공격자 계정 세션 주입 관점에서 별도 관찰 |
| `POST /api/auth/refresh` | Refresh 쿠키(`/api/auth`) | 토큰 회전 | SameSite=Lax, 명시 Origin, POST | SameSite 완화 시 최우선 CSRF 토큰 대상 |
| `POST /api/auth/logout` 및 인증 변경 API | Access/Refresh 쿠키 | 세션/DB 변경 | SameSite=Lax, 명시 Origin, unsafe method | CORS만 단독 방어로 간주하지 않음 |
| `POST/PUT/PATCH/DELETE /api/**` | Access 쿠키(`/`) | 업무 데이터 변경 | SameSite=Lax, 명시 Origin, 인증·인가 | 교차 사이트 SPA/iframe 도입 시 보강 필요 |
| `GET/POST /sso/**` | API 자격증명 공유 안 함 | 외부 인증 콜백 | `allowCredentials=false`, 서버 검증 세션 | 예외를 `/api/**`로 확대 금지 |
| `GET /api/auth/sso/complete` | 검증된 SSO 세션/상태 쿠키 | JWT 쿠키 발급 | 검증 사번 1회 소비, origin allowlist, safe next | 일반 상태 변경 GET의 선례로 확대 금지 |

### CSRF 보강 트리거와 목표 구현

다음 중 하나라도 발생하면 현재 `csrf.disable()` 유지는 금지됩니다. 해당 변경과 같은 배포 단위에서 CSRF 토큰 또는 동등한 서버 검증 Origin/nonce 방어를 활성화해야 합니다.

1. Access/Refresh/User/SSO 상태 쿠키 중 하나를 `SameSite=None`으로 변경한다.
2. credentialed `/api/**`에 새 교차 사이트 Origin을 추가하거나 wildcard/pattern으로 완화한다.
3. 프론트를 cross-site iframe에 임베드하거나 별도 사이트의 SPA가 쿠키 API를 호출한다.
4. GET으로 업무 상태 변경 엔드포인트를 추가한다.
5. `/sso/**`의 `allowCredentials`를 `true`로 변경한다.

목표 구현은 Spring Security `CookieCsrfTokenRepository` 또는 동등한 synchronizer/double-submit token을 사용합니다. 프론트의 `$apiFetch`/`useApiFetch`는 unsafe method에 토큰 헤더를 전송하고, 로그인·refresh·logout·SSO complete의 토큰 발급·회전 예외를 별도 테스트로 검증합니다. Origin/Referer 검증만으로 대체하려면 누락 헤더 정책과 신뢰 프록시 경계를 문서화하고 보안 리뷰 승인을 받아야 합니다.

## 운영 비밀값

DB 비밀번호, JWT secret, 외부 API 키는 환경변수로 주입합니다. `EnvironmentValidator`의 운영 기동 차단 규칙을 우회하지 않습니다.

## 로그인 보호

사번 기준 로그인 실패 횟수와 잠금은 DB 로그인 이력으로 판단합니다. IP·기기 기반 보호와 사용자 열거 방지처럼 미구현된 개선 사항은 `TASK.md`에서 관리합니다.
