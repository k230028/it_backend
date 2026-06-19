package com.pentasecurity.business;

import com.pentasecurity.config.ConfigureSetting;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


/**
 * Business - 최초로 호출되는 페이지
 *    인증서버 통신체크 한 후 이상이 없을 경우 인증서버의 로그인 페이지(SSO_LOGIN_PAGE)로 리다이렉션 처리
 */
public class Business extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public Business() {
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

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("[[[ business page ]]]");

        // 인증서버 통신 체크
        GetMethod method = null;
        try {
            HttpClient httpClient = new HttpClient();
            method = new GetMethod(ConfigureSetting.CHECK_SERVER_URL);
            httpClient.setConnectionTimeout(ConfigureSetting.connectionTimeout);
            httpClient.setTimeout(ConfigureSetting.soTimeout);

            httpClient.executeMethod(method);
            String httpResponse = method.getResponseBodyAsString();

            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject)jsonParser.parse(httpResponse);

            // debug print
            System.out.println("httpResponse : " + jsonObject);

            String resultCode = (String)jsonObject.get("resultCode");

            if (resultCode == null || resultCode.equals("000000") == false) {
                throw new Exception();
            }

        } catch (Exception e) {
            // SSO 인증서버와 통신이 되지 않을 경우 개별 로그인 처리
            System.out.println("[Business Exception] : " + e.toString());

            // TODO - 인증서버와 통신 실패 시 개별 업무로 로그인 할 수 있도록 처리 해야 합니다.
            response.sendRedirect(ConfigureSetting.EXCEPTION_PAGE);
        } finally {
            try {
                method.releaseConnection();
            } catch(Exception e) {}
        }

        // 사용자는 SSO 인증서버로 리다이렉션 하여 인증 유무를 체크 한다
        response.sendRedirect(ConfigureSetting.AUTH_LOGIN_PAGE + "?agentId=" + ConfigureSetting.AGENT_ID);
    }
    

}
