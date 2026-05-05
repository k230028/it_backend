# 백엔드 주석 작성 스타일

> `it_backend/CLAUDE.md`에서 분리된 주석 예시 자료입니다.
> 모든 JavaDoc 주석 및 인라인 주석은 **한글**로 작성합니다.

## 1. JavaDoc 표준 형식

- 첫 줄: 한 문장 요약
- 빈 줄 후 상세 설명 (필요 시 `[처리 순서]`, `[예외 처리]` 같은 소제목 블록 활용)
- `@param` / `@return` / `@throws` 명시
- 단순 대입·게터·트리비얼 메서드에는 주석을 추가하지 않습니다.

## 2. Service 메서드 예시 (인증)

```java
/**
 * 로그인 처리
 * 사번(eno)과 비밀번호를 검증하여 Access Token 및 Refresh Token을 발급합니다.
 *
 * [처리 순서]
 * 1. 사번으로 사용자 조회 → 미존재 시 예외 발생
 * 2. 비밀번호 SHA-256 해시 비교 → 불일치 시 실패 이력 저장 후 예외 발생
 * 3. Access Token / Refresh Token 생성
 * 4. Refresh Token DB 저장 (기존 토큰 덮어쓰기)
 * 5. 로그인 성공 이력 저장
 * 6. 컨트롤러에서 토큰을 httpOnly 쿠키로 내려보냄
 *
 * @param request   로그인 요청 DTO (eno, password)
 * @param clientIp  클라이언트 IP 주소 (이력 기록용)
 * @param userAgent 클라이언트 User-Agent (이력 기록용)
 * @return 로그인 응답 DTO (토큰은 @JsonIgnore 대상이며 쿠키로만 전달, 본문에는 사용자 정보 포함)
 * @throws CustomGeneralException 사용자 미존재 또는 비밀번호 불일치 시
 */
public AuthDto.LoginResponse login(AuthDto.LoginRequest request, String clientIp, String userAgent) {
    // 1. 사용자 조회
    CuserI user = cuserIRepository.findByEno(request.getEno())
        .orElseThrow(() -> new CustomGeneralException("존재하지 않는 사번입니다."));
    // 이하 로직 ...
}
```

## 3. Service 메서드 예시 (Soft Delete)

```java
/**
 * 프로젝트 논리 삭제
 * DEL_YN을 'Y'로 변경하며, 결재 진행 중인 신청서가 있을 경우 삭제를 거부합니다.
 *
 * @param prjMngNo 프로젝트 관리번호 (예: PRJ-2026-0001)
 * @throws CustomGeneralException 프로젝트 미존재 또는 결재 진행 중인 경우
 */
public void deleteProject(String prjMngNo) {
    // 프로젝트 존재 여부 확인
    Bprojm project = projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N")
        .orElseThrow(() -> new CustomGeneralException("존재하지 않는 프로젝트입니다."));
    // 이하 로직 ...
}
```

## 4. 파일 헤더 주석 가이드

- 해당 파일의 역할
- 주요 흐름 (호출되는 흐름이 비자명할 때)
- 연동 대상 API 또는 테이블

## 5. 인라인 주석 가이드

- "왜"를 설명. "무엇"은 코드만 봐도 알 수 있으면 생략.
- TODO/FIXME는 후속 조치가 가능한 문장으로 작성, 장기 과제는 루트 `TASK.md`에도 등록.
