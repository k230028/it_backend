# 인증과 인가 가이드

## JWT와 쿠키

- Access Token은 15분, Refresh Token은 7일입니다.
- 두 토큰은 httpOnly 쿠키로 전달합니다.
- Access Token에는 사번, 자격등급 목록과 부서코드를 포함합니다.
- Refresh Token 갱신 시 사용자 권한과 부서를 DB에서 다시 읽습니다.
- Refresh Token은 회전하며 유예 기간 이후 재사용이 탐지되면 사용자 토큰 패밀리를 폐기합니다.
- 운영에서는 Bearer 헤더 폴백을 비활성화하고 쿠키 인증을 기본으로 합니다.

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

## 운영 비밀값

DB 비밀번호, JWT secret, 외부 API 키는 환경변수로 주입합니다. `EnvironmentValidator`의 운영 기동 차단 규칙을 우회하지 않습니다.

## 로그인 보호

사번 기준 로그인 실패 횟수와 잠금은 DB 로그인 이력으로 판단합니다. IP·기기 기반 보호와 사용자 열거 방지처럼 미구현된 개선 사항은 `TASK.md`에서 관리합니다.
