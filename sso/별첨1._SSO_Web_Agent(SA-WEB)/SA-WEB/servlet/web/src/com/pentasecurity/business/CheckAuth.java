package com.pentasecurity.business;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.pentasecurity.config.ConfigureSetting;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * checkauth - SSO로부터 호출 되는 페이지
 *      토큰 검증 및 업데이트 진행
 */
public class CheckAuth extends HttpServlet {
	private static final long serialVersionUID = 1L;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public CheckAuth() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doPost(request, response);;
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("[[[ checkauth page ]]]");

        String resultCode = request.getParameter("resultCode") == null ? "" : request.getParameter("resultCode");
        String secureToken = request.getParameter("secureToken") == null ? "" : request.getParameter("secureToken");
        String secureSessionId = request.getParameter("secureSessionId") == null ? "" : request.getParameter("secureSessionId");
        String clientIp = request.getRemoteAddr();

        String resultMessage = "";
        String resultData = "";
        String returnUrl = "";

        System.out.println("resultCode : " + resultCode);
        System.out.println("secureToken : " + secureToken);
        System.out.println("secureSessionId : " + secureSessionId);
        System.out.println("clientIp : " + clientIp);

        if (resultCode.equals("000000") && "".equals(secureToken) == false && "".equals(resultCode) == false) {
            PostMethod method = null;
            try {
                // 인증서버에 토큰 검증 및 사용자 정보를 요청하기 위해 httpclient를 사용하여 전달
                method = new PostMethod(ConfigureSetting.TOKEN_AUTHORIZATION_URL);
                NameValuePair[] nameValuePair = {
                        new NameValuePair("secureToken", secureToken),
                        new NameValuePair("secureSessionId", secureSessionId),
                        new NameValuePair("requestData", ConfigureSetting.REQUEST_DATA),
                        new NameValuePair("agentId", ConfigureSetting.AGENT_ID),
                        new NameValuePair("clientIP", clientIp)
                };

                method.setQueryString(nameValuePair);

                HttpClient httpClient = new HttpClient();
                httpClient.setConnectionTimeout(ConfigureSetting.connectionTimeout);
                httpClient.setTimeout(ConfigureSetting.soTimeout);

                int status_code = httpClient.executeMethod(method);
                // 정상적으로 호출이 되지 않았을 경우 Exception 처리
                if (status_code != HttpStatus.SC_OK) {
                    throw new HttpException(method.getStatusLine().toString());
                }

                String httpResponse = method.getResponseBodyAsString();
                System.out.println("httpResponse : " + httpResponse);
                JSONParser jsonParser = new JSONParser();
                JSONObject jsonObject = (JSONObject) jsonParser.parse(httpResponse);

                // 사용자 요청 정보
                JSONObject dataObject = (JSONObject) jsonObject.get("user");
                // 결과 코드와 메시지
                resultCode = (String) jsonObject.get("resultCode");
                resultMessage = (String) jsonObject.get("resultMessage");
                // Return URL(인증서버에서 리다이렉션될 주소를 전달)
                returnUrl = (String) jsonObject.get("returnUrl");

                // check cs mode(토큰저장소에 토큰을 저장하기 위해 사용되며 CS모드일 경우는 SAVE_TOKEN_URL로 리다이렉션 됨)
                boolean useCSMode  = jsonObject.get("useCSMode") == null ? false:Boolean.valueOf(jsonObject.get("useCSMode").toString());

                // 요청 데이터 정보 추출
                if ("000000".equals(resultCode)) {
                    // 검증 성공
                    String[] keys = ConfigureSetting.REQUEST_DATA.split(",");

                    for (int i = 0; i < keys.length; i++) {
                        String temp = (String) dataObject.get(keys[i]);
                        if (temp == null) {
                            continue;
                        }

                        if ("".equals(resultData)) {
                            resultData = temp;
                        } else {
                            resultData = resultData + "," + temp;
                        }
                    }

                    // cs mode 체크 하여 saveToken page 호출 여부 판단
                    if (useCSMode) {
                        returnUrl = ConfigureSetting.SAVE_TOKEN_URL;
                    }

                } else if ("310017".equals(resultCode) || "310012".equals(resultCode)) {
                    // 서비스 접근 권한 실패(다른 서비스에 영향을 주어서는 안됨으로 로그아웃은 하지 않음)
                    returnUrl = ConfigureSetting.SERVICE_ERR_PAGE;
                } else {
                    // SSO 검증 실패(로그아웃 필요)
                    returnUrl = ConfigureSetting.ERROR_PAGE;
                }

                HttpSession session = request.getSession();
                // 결과 코드와 메시지, 사용자 요청 데이터를 세션에 저장
                session.setAttribute("resultCode", resultCode);
                session.setAttribute("resultMessage", resultMessage);
                session.setAttribute("resultData", resultData);
                session.setAttribute("secureSessionId", secureSessionId);

            } catch (HttpException e) {
                System.out.println("[checkauth HttpException] : " + e.toString());

                // TODO - 인증서버와 통신 실패 시 개별 업무로 로그인 할 수 있도록 처리 해야 합니다.
                returnUrl = ConfigureSetting.EXCEPTION_PAGE;
            } catch (Exception e) {
                System.out.println("[checkauth Exception] : " + e.toString());

                returnUrl = ConfigureSetting.ERROR_PAGE;
            } finally {
                try {
                    method.releaseConnection();
                } catch(Exception e) {}
            }

        } else {
            // 비정상 호출 할 경우 Business 페이지로 리다이렉션 처리
            System.out.println("unknown call");
            returnUrl = ConfigureSetting.LOGOUT_PAGE;
        }

        response.sendRedirect(returnUrl);
	}
}
