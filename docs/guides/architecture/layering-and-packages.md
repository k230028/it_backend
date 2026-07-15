# 레이어와 패키지 가이드

## 호출 방향

```text
Controller → Service → Repository → Oracle
```

- Controller는 HTTP 계약, 입력 검증과 인증 주체 전달을 담당합니다.
- Service는 트랜잭션, 권한, 소유권과 업무 규칙을 담당합니다.
- Repository는 JPA·QueryDSL·네이티브 SQL 데이터 접근을 담당합니다.
- DTO는 계층 경계를 넘는 데이터 계약이며 엔티티를 API 응답으로 직접 노출하지 않습니다.

## 패키지 책임

| 패키지      | 책임                                                  |
| ----------- | ----------------------------------------------------- |
| `config`    | Security, JPA, QueryDSL, Swagger, Web 설정            |
| `common`    | 인증, IAM, 결재, 게시판, 코드, 알림, 관리자 공통 기능 |
| `domain`    | 예산, 협의회, 사업 집행, 메뉴, 감사 도메인            |
| `infra`     | 파일, AI, EAI 등 외부 기술 연동                       |
| `exception` | 예외 응답 변환                                        |

일반적으로 `domain → common`, `infra → common`을 허용합니다. 결재와 사업 상태 동기화 때문에 `common.approval → domain.budget.project` 의존이 존재하므로 패키지명만 보고 일괄 금지하지 않습니다. 신규 의존은 업무 필요와 순환 참조 여부를 함께 검토합니다.

## 트랜잭션

- 조회 중심 서비스는 클래스 수준 `@Transactional(readOnly = true)`를 사용할 수 있습니다.
- 쓰기 메서드는 `@Transactional`로 명시적으로 덮어씁니다.
- 외부 HTTP 호출은 불필요하게 DB 트랜잭션을 점유하지 않도록 경계를 분리합니다.
- 핵심 상태 동기화는 같은 트랜잭션, 알림·메일은 커밋 이후 독립 트랜잭션을 사용합니다.
