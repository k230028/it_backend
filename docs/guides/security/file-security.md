# 파일 보안 가이드

- 업로드 진입 시 `FileValidator.validateExtension()`으로 허용 확장자를 확인합니다.
- 메타 수정과 삭제는 `FileOwnershipChecker.verifyWriteAccess()`로 owner-or-admin을 검증합니다.
- 다운로드·미리보기·단건 조회는 `checkReadAccess()`, 목록은 `canRead()`로 필터링합니다.
- 원본 기준 일괄 삭제도 서비스 계층에서 소유권을 확인합니다.
- 새 `orcDtt`를 추가할 때는 읽기 정책을 함께 등록합니다.
- 파일명·경로를 조합할 때 기준 저장 디렉터리 밖으로 벗어나지 않도록 정규화합니다.
- Gemini 등 외부 서비스에 첨부를 전달하기 전에 파일 접근 권한, 개수, 크기와 비용 상한을 검증합니다.

`/api/files/**`가 인증을 요구한다는 사실만으로 개별 파일 소유권 검증을 대체할 수 없습니다.
