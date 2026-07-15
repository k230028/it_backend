# Tiptap 변수 가이드

Tiptap 문서의 토큰을 서버 데이터로 해석합니다.

## 형식

```text
<YEAR>.<CATEGORY>[.<PROJECT_CODE>].<ITEM>
```

예:

- `2026.itBudget.requestAmount`
- `2026.capBudget.allocatedAmount`
- `2026.proj.P001.allocationRate`

카테고리는 전산예산, 자본예산, 일반관리비와 사업별 항목을 지원합니다.

## API

- `GET /api/tiptap-variables/metadata`: 카테고리·연도·사업·항목 카탈로그
- `POST /api/tiptap-variables/resolve`: 토큰 일괄 해석

해석 상태는 `OK`, `INVALID`, `MISSING`, `FORBIDDEN`을 구분합니다. 데이터 없음과 권한 없음을 같은 빈 문자열 상태로 축약하지 않습니다. 사업 토큰은 역할과 향후 부서·사업 범위를 적용합니다.

금액과 비율 포맷은 서버의 단일 포맷 함수에서 관리합니다. 요청 토큰 개수 제한과 빈 요청 단축 경로를 유지합니다.
