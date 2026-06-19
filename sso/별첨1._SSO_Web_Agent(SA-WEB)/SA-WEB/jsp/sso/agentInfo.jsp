<%!
    /************************************************************
     *  Web-Agent 환경 설정   (** 수정)
     ************************************************************/	
	//[필독.1] 내부망 ISign+ SSO URL 적용 예
    //private static final String AUTH_URL = "dintesso.kdb.co.kr:20080"; 
    private static final String AUTH_SSL_URL = "dintesso.kdb.co.kr:20443"; // PC(브라우저) -> ESSO 서버 통신용
	private static final String AUTH_SSL_HOST_URL = AUTH_SSL_URL; // 업무서버(AP) -> ESSO 서버 통신용

	//[필독.2] 인터넷망(스마트워크망) ISign+ SSO URL 적용 예
	//private static final String AUTH_URL = "dextesso.kdb.co.kr:20080"; //
	//private static final String AUTH_SSL_URL = "dextesso.kdb.co.kr:20443"; // PC(브라우저) -> ESSO 서버 통신용
	//private static final String AUTH_SSL_HOST_URL = "dessej01.kdb.co.kr:20443"; // 업무서버(AP) -> ESSO 서버 통신용

	private static final String AUTHORIZATION_URL = "https://" + AUTH_SSL_URL; // SSL 적용(PC(브라우저) -> ESSO 서버 통신용) 
	private static final String AUTHORIZATION_HOST_URL = "https://" + AUTH_SSL_HOST_URL; // SSL 적용 (업무서버(AP)-> ESSO 서버 통신용)
	
/**/private static final String agentId = "3";                      // 업무 시스템 고유 번호(관리자가 할당한 번호)
/**/private static final String requestData = "id";                 // 요청 데이터(세션에 저장될 값)


    /************************************************************
     *  Agent Page
     ************************************************************/
    private static final String BUSINESS_PAGE = "business.jsp";     // SSO 에이전트 호출 시작 페이지
    private static final String LOGOUT_PAGE = "logout.jsp";         // SSO 에이전트 로그아웃 페이지
    private static final String ERROR_PAGE = "error.jsp";           // SSO 인증 실패시 호출 되는 페이지(에러 출력용)
    private static final String EXCEPTION_PAGE = "#";               // SSO 네트워크 통신 실패 시 기존 업무로 로그인 페이지 처리나 에러 화면으로 리다이렉션


    /************************************************************
     *  HttpClient Timeout 설정
     ************************************************************/
    private static final int connectionTimeout = 5000;	    // 서버 응답 시간 한도 설정
    private static final int soTimeout = 5000;			    // 연결 후 Read 하는 동안 특정 시각동안 패킷이 없을 경우 Connection 종료
    private static final int maxTotalConnections = 500;     // keepalive 연결


    /************************************************************
     *  ISign+ API URL
     ************************************************************/  
	private static final String CHECK_SERVER_URL = AUTHORIZATION_HOST_URL + "/openapi/checkserver";
    private static final String TOKEN_AUTHORIZATION_URL = AUTHORIZATION_HOST_URL + "/token/authorization";


    private static final String SAVE_TOKEN_URL = AUTHORIZATION_URL + "/token/saveToken.html";
    private static final String AUTH_LOGIN_PAGE = AUTHORIZATION_URL + "/login.html";
    private static final String AUTH_LOGOUT_PAGE = AUTHORIZATION_URL + "/logout.html";
    private static final String SERVICE_ERR_PAGE = AUTHORIZATION_URL + "/serviceError.html";

%>