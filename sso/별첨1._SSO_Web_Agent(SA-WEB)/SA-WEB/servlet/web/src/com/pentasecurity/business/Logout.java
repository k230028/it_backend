package com.pentasecurity.business;

import com.pentasecurity.config.ConfigureSetting;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * logout - SSO 전체 로그아웃을 진행
 *      기존 업무의 로그아웃처리를 먼저 진행 후 호출 합니다
 */
public class Logout extends HttpServlet {
	private static final long serialVersionUID = 1L;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public Logout() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // TODO - 업무 시스템 로그아웃 로직 처리

        HttpSession session = request.getSession();

        try {
            session.invalidate();
        } catch (Exception e) {};

        response.sendRedirect(ConfigureSetting.AUTH_LOGOUT_PAGE + "?agentId=" + ConfigureSetting.AGENT_ID);
	}

}
