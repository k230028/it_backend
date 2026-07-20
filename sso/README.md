# SSO 벤더 참고자료

이 디렉터리는 SSO Web Agent 공급사가 제공한 샘플과 원본 배포 자료를 보관한다. 제품의 Gradle `main`·`test` 소스셋에 포함되지 않으며 운영 WAR에도 패키징하지 않는다.

- 제품 SSO 구현의 단일 진실 공급원은 `src/main/java/com/kdb/it/common/sso`이다.
- 이 디렉터리의 JSP·Java·class 파일은 직접 수정하거나 제품 코드에서 import하지 않는다.
- 보안 정적분석 도입 시 `it_backend/sso/**`를 vendor/non-production 제외 목록에 명시한다.
- 자료의 소유자는 백엔드 SSO 운영 담당자이며, 계약 확인과 장애 대응 참고 목적으로만 열람한다.
- 삭제는 SSO 공급 계약, 장애 대응 자료 보존 기간, 운영 담당자 승인을 확인한 별도 작업에서 결정한다.
