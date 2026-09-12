# 파일 보안 가이드

## 업로드·쓰기·경로

- 업로드 진입 시 `FileValidator.validateExtension()`으로 허용 확장자를 확인합니다.
- 메타 수정과 삭제는 `FileOwnershipChecker.verifyWriteAccess()`로 owner-or-admin을 검증합니다.
- 원본 기준 일괄 삭제도 서비스 계층에서 소유권을 확인합니다.
- 파일명·경로를 조합할 때 기준 저장 디렉터리 밖으로 벗어나지 않도록 정규화합니다.
- Gemini 등 외부 서비스에 첨부를 전달하기 전에 파일 접근 권한, 개수, 크기와 비용 상한을 검증합니다.
- 같은 물리 파일을 여러 부모에 붙일 때는 `FileService.linkExistingFile`로 메타행만 추가합니다(디스크 기록은 파일당 1회). 이 공유는 삭제가 논리 삭제(`DEL_YN='Y'`)라 형제 행의 다운로드가 깨지지 않는다는 전제 위에 있으므로, 물리 삭제를 도입하면 이 경로를 함께 고칩니다. 이 메서드는 권한 검증 없는 내부 전용 경로입니다 — 컨트롤러·사용자 요청 흐름에서 직접 호출하지 않으며, 새 호출자를 추가할 때는 호출자 측에서 대상 종류·부모 쓰기 권한을 검증합니다.

## 파일 읽기 인가 (SEC-05)

파일 읽기는 파일 종류(`APG_FL_KD_NM`)별 authorizer가 부모 자원의 읽기 권한을 재사용해 판정합니다. 이전의 "비게시판 업무 파일 읽기는 별도 소유권 제한 없음" 방침은 폐기되었고, 목록·메타·다운로드·미리보기 네 경로가 모두 같은 판정을 통과합니다.

- 목록은 `FileService.getFiles`가 `FileOwnershipChecker.canRead()`로 필터링하고, 메타·다운로드·미리보기 단건은 `checkReadAccess()`로 검증합니다.
- 판정은 `FileReadAuthorizerRegistry`에 위임하며, 레지스트리는 기동 시 종류별 `FileReadAuthorizer` 구현을 수집합니다. 같은 종류를 두 authorizer가 등록하면 기동이 `IllegalStateException`으로 실패합니다.
- **default-deny(fail-safe):** 미등록 종류, `null` 종류, `null` 부모 ID는 관리자만 읽을 수 있습니다. 비인증(익명) 사용자는 공개 게시판 파일을 포함해 모든 파일 읽기가 거부됩니다.

### 종류별 규칙

| `APG_FL_KD_NM` | 부모 | 읽기 허용 |
| --- | --- | --- |
| `공통게시판` | `Cblbcm`(`NAC_MNG_NO`) | 관리자 OR 게시물 공개(화면표시 `sreYn=Y` + 공개기간 `sttDt~endDt` 내) |
| `요구사항정의서` | `Brdocm`(`DOC_MNG_NO`, 최신 버전) | 관리자 OR 작성자(`FST_ENR_USID`) OR 주관부서(`SVN_DPM_C == 사용자 bbrC`) |
| `사업계획서`·`타당성검토표`·`협의회관련자료` | 협의회(`IT_PTL_ASCT_ID`) | 관리자/정보보안관리자 OR 해당 협의회 위원 OR 관련부서(협의회 사업 `BPROJM.SVN_DPM_C == 사용자 bbrC`) |
| `가이드문서` | `Bgdocm` | 인증 사용자 전체(전사 공개, default-deny의 명시적 예외) |
| `다이어그램` | Excalidraw를 삽입한 모든 화면 | 인증 사용자 전체. 장면 파일과 장면 내부 이미지에 동일하게 적용 |
| `정보화사업` | `Bprojm`(`ABUS_MNG_NO`, `DEL_YN='N'`) | 인증 사용자 전체(사업 상세 API와 같은 범위, default-deny의 명시적 예외). 사업이 없거나 삭제되었으면 거부. 경상사업도 같은 원장이라 종류를 공유 |
| `전산업무비` | `Bcostm` 현재 최종본(`BG_NO`, `LST_YN='Y'`, `DEL_YN='N'`) | 관리자 또는 IT 조직 사용자 또는 담당부서 사용자 |
| `편성요청서반입` | 반입받은 신청서번호 → `Cappla` 매핑이 가리키는 원장(`BPROJM`·`BCOSTM`) | 관리자 OR 연결 원장의 주관부서(`SVN_DPM_C == 사용자 bbrC`). 매핑·원장이 없으면 거부 |
| (미등록·`null` 종류) | — | 관리자만(default-deny) |

- 새 파일 종류(`APG_FL_KD_NM`)를 추가하면 authorizer를 등록하기 전까지 default-deny(관리자만)로 처리되므로, 필요한 읽기 규칙은 authorizer로 명시 등록합니다.
- 목록 인가 판정은 `(APG_FL_KD_NM, APG_FL_LNK_CTZ_NM)` 요청 범위 캐시로 재사용해 부모 조회 N+1을 방지합니다.

### 읽기 거부 계약

- 읽기 권한이 없으면 `checkReadAccess()`가 `AccessDeniedException`을 던지고 `GlobalExceptionHandler`가 403으로 매핑합니다.
- 파일이 없으면 기존 `CustomGeneralException` 계약을 유지해 "권한 없음(403)"과 "파일 없음"을 구분합니다.

### 파일 전용 정책 경계

파일 authorizer는 부모 리소스의 현재 HTTP 엔드포인트를 재사용하는 것이 아니라, 문서화된 소유자·부서·위원 규칙을 파일 읽기에 적용하는 더 엄격한 전용 정책입니다. 부모 상세 API의 인가 수준이 이 파일 정책보다 느슨하더라도 파일 권한을 완화하지 않습니다.

## 첨부 대상 쓰기 인가

신규 첨부 대상 종류는 `FileTargetWriteAuthorizer`를 구현해 등록합니다. `FileTargetWriteAuthorizerRegistry`가 기동 시 수집하며 같은 종류 중복 등록은 `IllegalStateException`으로 기동이 실패합니다(읽기 레지스트리와 동일).

- **읽기와 기본값이 다릅니다**: 읽기는 default-deny(미등록 종류는 관리자만), 쓰기는 미등록 레거시 종류의 기존 업로드 동작을 보존합니다. 등록된 종류만 부모 존재와 작성 권한(`canWrite`)을 강제합니다.
- `정보화사업`은 사업 본문 수정과 같은 규칙(`OwnershipVerifier.verifyModifiable`: 관리자 OR 최초 작성자 OR 주관부서가 같은 사용자)을 씁니다. 본문을 못 고치는 사용자가 첨부만 바꿀 수 있는 틈을 만들지 않기 위해서입니다.
- `전산업무비` 업로드는 현재 최종·미삭제 `Bcostm`을 기준으로 관리자·최초 작성자·담당부서 사용자에게만 허용합니다. 기존 파일 행의 삭제는 대상 쓰기 권한과 별도로 업로더 또는 관리자 계약을 따릅니다.
- `allowsGenericMutation() == false`인 종류(현재 `편성요청서반입`)는 generic 파일 API의 신규 연결·수정·삭제가 모두 거부되고(관리자 포함), 전용 writer(`RequestFormSourceFileArchiver`)의 내부 경로만 파일을 만듭니다. 이 내부 경로는 컨트롤러의 대상 쓰기 레지스트리를 거치지 않습니다.

## 데이터 정합성

- 레거시로 뒤바뀐 부모 키(`APG_FL_KD_NM`/`APG_FL_LNK_CTZ_NM`)는 정규화 마이그레이션으로 교정하고, 부모를 복구할 수 없는 격리 행은 관리자만 접근할 수 있습니다. 정규화 SQL·격리 목록·수동 재연결 절차는 `file-read-migration.md`로 관리합니다.

`/api/files/**`가 인증을 요구한다는 사실만으로 개별 파일 소유권 검증을 대체할 수 없습니다.

## 물리 저장 폴더

- 첨부파일 종류(`APG_FL_KD_NM`)와 영문 물리 폴더의 대응은 `FileStoragePathPolicy.directoryName`이 SoT입니다. 매핑에 없는 종류는 업로드 시 예외로 실패하므로, 새 종류를 판정기에 등록할 때 이 매핑도 함께 추가합니다.
- 과거 한글 폴더명으로 저장된 파일은 `resolveStoredDirectory`가 읽기 시점에 해석합니다. 새 코드가 한글 폴더를 만들거나 경로 문자열로 권한을 판단하지 않습니다.
