# 백엔드 주석 스타일

모든 신규 JavaDoc과 인라인 주석은 한글로 작성합니다.

## JavaDoc

- 첫 문장에 책임을 요약합니다.
- 입력값, 반환값과 실패 조건을 기록합니다.
- 비자명한 처리 순서나 트랜잭션·권한 이유를 설명합니다.
- 단순 게터·대입·코드 그대로 읽히는 내용에는 주석을 추가하지 않습니다.

```java
/**
 * 프로젝트를 논리 삭제합니다.
 *
 * @param projectId 프로젝트 식별자
 * @throws AccessDeniedException 현재 사용자가 소유자나 관리자가 아닌 경우
 * @throws CustomGeneralException 프로젝트가 없거나 삭제 가능한 상태가 아닌 경우
 */
public void deleteProject(String projectId) {
    // 권한 검증을 상태 변경보다 먼저 수행해 존재 여부 노출 범위를 제한합니다.
}
```

## 인라인 주석

무엇을 하는지보다 왜 필요한지 설명합니다. TODO/FIXME는 후속 행동이 가능한 문장으로 작성하고 장기 과제는 루트 `TASK.md`에 등록합니다.
