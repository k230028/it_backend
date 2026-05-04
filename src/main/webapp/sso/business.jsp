<%@ page contentType="text/html;charset=UTF-8" %>
<%--
  테스트용 SSO 로그인 진입 페이지

  역할:
  - 운영 SSO 제품의 business.jsp가 위치할 URL을 로컬 테스트에서도 동일하게 제공합니다.
  - 프론트엔드가 미인증 사용자를 /sso/business.jsp?next={원본경로}로 보내면
    실제 운영에서는 이 JSP가 SSO 서버 세션 확인 또는 로그인 요청을 시작합니다.
  - 이 테스트 JSP는 별도 인증 화면 없이 곧바로 agentProc.jsp로 이동해
    "SSO 인증이 진행되었다"는 다음 단계를 흉내 냅니다.

  주의:
  - bootRun 환경에서는 SsoController가 같은 URL을 컨트롤러로 처리하므로,
    이 JSP는 WAR 배포 또는 실제 벤더 JSP 교체 시 참고용입니다.
  - 실제 연동 시에는 vendor business.jsp가 next/RelayState 같은 복귀 경로를
    SSO 인증 결과 단계까지 보존하도록 설정해야 합니다.

  실제 SSO 연동 시 이 파일을 벤더 제공 business.jsp로 교체합니다.
--%>
<%
    response.sendRedirect(request.getContextPath() + "/sso/agentProc.jsp");
%>
