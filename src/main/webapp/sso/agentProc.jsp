<%@ page contentType="text/html;charset=UTF-8" %>
<%--
  SSO 인증 완료 콜백

  [테스트] SSO Agent가 없으면 ?eno=테스트사번 파라미터로 resultData를 대체할 수 있습니다.

  [실제 SSO 연동 시] 이 파일 전체를 벤더 제공 agentProc.jsp로 교체합니다.
  벤더 파일 안에서도 성공 시 아래 핵심 흐름은 유지해야 합니다.
    1. session.resultCode == "000000" 확인
    2. session.resultData에서 SSO가 검증한 사용자 식별자(행번)를 추출
    3. session.ssoVerifiedEno에 저장
    4. /api/auth/sso/complete로 리다이렉트
--%>
<%
    String resultCode = session.getAttribute("resultCode") == null ? "" : session.getAttribute("resultCode").toString();
    String resultMessage = session.getAttribute("resultMessage") == null ? "" : session.getAttribute("resultMessage").toString();
    String resultData = session.getAttribute("resultData") == null ? "" : session.getAttribute("resultData").toString();
    String next   = request.getParameter("next");
    String origin = request.getParameter("origin");

    // 로컬 SSO Agent 부재 시 테스트 편의를 위해 query eno를 resultData처럼 취급합니다.
    if (resultCode.isBlank() && resultData.isBlank()) {
        String testEno = request.getParameter("eno");
        if (testEno == null || testEno.isBlank()) {
            testEno = "K130024";
        }
        resultCode = "000000";
        resultData = testEno;
    }

    StringBuilder redirect = new StringBuilder(request.getContextPath() + "/api/auth/sso/complete");
    String sep = "?";
    if (next != null && !next.isBlank()) {
        redirect.append(sep).append("next=").append(java.net.URLEncoder.encode(next, "UTF-8"));
        sep = "&";
    }
    if (origin != null && !origin.isBlank()) {
        redirect.append(sep).append("origin=").append(java.net.URLEncoder.encode(origin, "UTF-8"));
    }

    if (!"000000".equals(resultCode) || resultData.isBlank()) {
        response.sendRedirect(redirect.toString());
        return;
    }

    session.setAttribute("ssoVerifiedEno", resultData);
    response.sendRedirect(redirect.toString());
%>
