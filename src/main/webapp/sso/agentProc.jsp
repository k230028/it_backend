<%@ page contentType="text/html;charset=UTF-8" %>
<%--
  테스트용 SSO 인증 콜백 페이지 — 항상 K140024로 인증 성공 처리

  역할:
  - 운영 SSO 제품의 agentProc.jsp가 위치할 URL을 로컬 테스트에서도 동일하게 제공합니다.
  - 실제 agentProc.jsp는 SSO 서버가 돌려준 인증 결과를 Agent 라이브러리로 검증하고,
    성공 시 사용자 식별자(사번)를 애플리케이션에 전달합니다.
  - 이 테스트 JSP는 검증 과정을 생략하고 고정 사번 K140024를 사용해
    SSO 인증 성공 결과를 재현합니다.

  다음 단계:
  - /api/auth/sso/complete는 전달받은 사번으로 IT Portal 사용자를 조회합니다.
  - 조회가 성공하면 Access Token, Refresh Token, it-portal-user 쿠키를 발급합니다.
  - 이후 프론트엔드 원 요청 경로로 돌아가 Nuxt 인증 상태가 복원됩니다.

  주의:
  - 운영에서는 eno 쿼리 파라미터를 임의로 만들면 안 됩니다.
    반드시 SSO Agent가 검증한 사용자 식별자만 complete 단계로 전달해야 합니다.

  실제 SSO 연동 시 이 파일을 벤더 제공 agentProc.jsp로 교체합니다.
  성공 시 /api/auth/sso/complete?eno={사번} 으로 리다이렉트합니다.
--%>
<%
    // 테스트용 고정 사번입니다. 실제 SSO에서는 ssoAgent.getUserId(request) 등 검증된 API 결과로 대체합니다.
    String eno = "K140024";
    response.sendRedirect(request.getContextPath() + "/api/auth/sso/complete?eno=" + eno);
%>
