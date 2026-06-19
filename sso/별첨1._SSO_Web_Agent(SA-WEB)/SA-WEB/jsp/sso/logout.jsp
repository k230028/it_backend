<%@ page language="java" contentType="text/html; charset=UTF-8"%>
<%@ include file="agentInfo.jsp"%>
<%
    /**
     * logout - SSO 전체 로그아웃을 진행
     *      기존 업무의 로그아웃처리를 먼저 진행 후 호출 합니다
     */

    // TODO - 업무 시스템 로그아웃 로직 처리

    try {
        session.invalidate();
    } catch (Exception e) {};


    response.sendRedirect(AUTH_LOGOUT_PAGE);
%>