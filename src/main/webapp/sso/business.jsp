<%@ page contentType="text/html;charset=UTF-8" %>
<%--
  SSO 인증 진입점

  실제 SSO 연동 시 이 파일을 벤더 제공 business.jsp로 교체합니다.
  벤더 파일은 SSO 서버로 인증 요청을 보내고, 완료 후 agentProc.jsp로 이동합니다.
  next/origin 파라미터를 agentProc.jsp까지 유지해야 SSO 완료 후 원래 경로로 복귀합니다.
--%>
<%
    String next   = request.getParameter("next");
    String origin = request.getParameter("origin");

    StringBuilder redirect = new StringBuilder(request.getContextPath() + "/sso/agentProc.jsp");
    String sep = "?";
    if (next != null && !next.isBlank()) {
        redirect.append(sep).append("next=").append(java.net.URLEncoder.encode(next, "UTF-8"));
        sep = "&";
    }
    if (origin != null && !origin.isBlank()) {
        redirect.append(sep).append("origin=").append(java.net.URLEncoder.encode(origin, "UTF-8"));
    }
    response.sendRedirect(redirect.toString());
%>
