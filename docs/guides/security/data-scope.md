# 데이터 접근 범위 가이드

## 기본 원칙

- 프론트에서 전달한 부서코드나 역할을 권한 근거로 신뢰하지 않습니다.
- 인증 사용자의 JWT·DB 권한과 업무 데이터의 소유자·부서를 서버에서 비교합니다.
- 목록·상세·수정·삭제 각각에 필요한 범위를 명시합니다.

## 부서 필터

Controller의 `bbrC`는 조회 조건 표현일 뿐 최종 권한이 아닙니다. Service가 관리자 여부와 사용자 부서를 확인한 후 Repository 조건을 구성합니다.

```java
if (StringUtils.hasText(allowedBbrC)) {
    builder.and(entity.bbrC.eq(allowedBbrC));
}
```

`bbrC`가 비어 있다는 이유만으로 일반 사용자에게 전체 조회를 허용하지 않습니다. SSO 미동기화 계정 처리도 API별 정책으로 결정합니다.

## 소유권

- 공통 owner-or-admin 검증은 `OwnershipVerifier.verifyOwnerOrAdmin`을 사용합니다.
- 관리자 전용 상태 전이는 `verifyAdmin`을 사용합니다.
- 게시물·댓글·문서·파일은 읽기와 쓰기 권한을 각각 확인합니다.

## 작성자 조직 스냅샷

작성 시점 조직은 `AuthorOrgResolver.resolveCurrent()`, 조직명은 `OrgNameResolver.resolveName()`을 사용합니다. JWT를 직접 파싱하거나 화면에서 받은 조직명을 저장하지 않습니다.

부서 제한과 관리자 전체 조회, null·불일치 케이스를 테스트합니다.
