# 파일 보안 가이드

## 업로드·쓰기·경로

- 업로드 진입 시 `FileValidator.validateExtension()`으로 허용 확장자를 확인합니다.
- 메타 수정과 삭제는 `FileOwnershipChecker.verifyWriteAccess()`로 owner-or-admin을 검증합니다.
- 원본 기준 일괄 삭제도 서비스 계층에서 소유권을 확인합니다.
- 파일명·경로를 조합할 때 기준 저장 디렉터리 밖으로 벗어나지 않도록 정규화합니다.
- Gemini 등 외부 서비스에 첨부를 전달하기 전에 파일 접근 권한, 개수, 크기와 비용 상한을 검증합니다.

## 파일 읽기 인가 (SEC-05)

파일 읽기는 파일 종류(`PK_COL_NM`)별 authorizer가 부모 자원의 읽기 권한을 재사용해 판정합니다. 이전의 "비게시판 업무 파일 읽기는 별도 소유권 제한 없음" 방침은 폐기되었고, 목록·메타·다운로드·미리보기 네 경로가 모두 같은 판정을 통과합니다.

- 목록은 `FileService.getFiles`가 `FileOwnershipChecker.canRead()`로 필터링하고, 메타·다운로드·미리보기 단건은 `checkReadAccess()`로 검증합니다.
- 판정은 `FileReadAuthorizerRegistry`에 위임하며, 레지스트리는 기동 시 종류별 `FileReadAuthorizer` 구현을 수집합니다. 같은 종류를 두 authorizer가 등록하면 기동이 `IllegalStateException`으로 실패합니다.
- **default-deny(fail-safe):** 미등록 종류, `null` 종류, `null` 부모 ID는 관리자만 읽을 수 있습니다. 비인증(익명) 사용자는 공개 게시판 파일을 포함해 모든 파일 읽기가 거부됩니다.

### 종류별 규칙

| `PK_COL_NM` | 부모 | 읽기 허용 |
| --- | --- | --- |
| `공통게시판` | `Cblbcm`(`NAC_MNG_NO`) | 관리자 OR 게시물 공개(화면표시 `sreYn=Y` + 공개기간 `sttDt~endDt` 내) |
| `요구사항정의서` | `Brdocm`(`DOC_MNG_NO`, 최신 버전) | 관리자 OR 작성자(`FST_ENR_USID`) OR 주관부서(`SVN_DPM_C == 사용자 bbrC`) |
| `사업계획서`·`타당성검토표`·`협의회관련자료` | 협의회(`IT_PTL_ASCT_ID`) | 관리자/정보보안관리자 OR 해당 협의회 위원 OR 관련부서(협의회 사업 `BPROJM.SVN_DPM_C == 사용자 bbrC`) |
| `가이드문서` | `Bgdocm` | 인증 사용자 전체(전사 공개, default-deny의 명시적 예외) |
| (미등록·`null` 종류) | — | 관리자만(default-deny) |

- 새 파일 종류(`PK_COL_NM`)를 추가하면 authorizer를 등록하기 전까지 default-deny(관리자만)로 처리되므로, 필요한 읽기 규칙은 authorizer로 명시 등록합니다.
- 목록 인가 판정은 `(PK_COL_NM, PK_CONE)` 요청 범위 캐시로 재사용해 부모 조회 N+1을 방지합니다.

### 읽기 거부 계약

- 읽기 권한이 없으면 `checkReadAccess()`가 `AccessDeniedException`을 던지고 `GlobalExceptionHandler`가 403으로 매핑합니다.
- 파일이 없으면 기존 `CustomGeneralException` 계약을 유지해 "권한 없음(403)"과 "파일 없음"을 구분합니다.

### 파일 전용 정책 경계

파일 authorizer는 부모 리소스의 현재 HTTP 엔드포인트를 재사용하는 것이 아니라, 문서화된 소유자·부서·위원 규칙을 파일 읽기에 적용하는 더 엄격한 전용 정책입니다. 부모 상세 API의 인가 수준이 이 파일 정책보다 느슨하더라도 파일 권한을 완화하지 않습니다.

## 데이터 정합성

- 레거시로 뒤바뀐 부모 키(`PK_COL_NM`/`PK_CONE`)는 정규화 마이그레이션으로 교정하고, 부모를 복구할 수 없는 격리 행은 관리자만 접근할 수 있습니다. 정규화 SQL·격리 목록·수동 재연결 절차는 `file-read-migration.md`로 관리합니다. (해당 마이그레이션의 로컬 적용은 별도 Flyway 결함으로 현재 보류 중입니다.)

`/api/files/**`가 인증을 요구한다는 사실만으로 개별 파일 소유권 검증을 대체할 수 없습니다.
