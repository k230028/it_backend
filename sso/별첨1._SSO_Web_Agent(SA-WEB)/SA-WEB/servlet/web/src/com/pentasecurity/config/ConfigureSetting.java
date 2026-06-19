package com.pentasecurity.config;


import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Properties;

public class ConfigureSetting {
    /**
     *  ISign+ SSO URL
     */
    public static String AUTHORIZATION_URL;
    public static String AGENT_ID;
    public static String REQUEST_DATA;

    public static String AUTH_LOGIN_PAGE;
    public static String CHECK_SERVER_URL;
    public static String EXCEPTION_PAGE;
    public static String TOKEN_AUTHORIZATION_URL;
    public static String SAVE_TOKEN_URL;
    public static String SERVICE_ERR_PAGE;
    public static String AUTH_LOGOUT_PAGE;

    public static String ERROR_PAGE;
    public static String LOGOUT_PAGE;

    public static int connectionTimeout;
    public static int soTimeout;

    public static boolean get_Configuration_Setting() {
        /* property로부터 얻은 환경 설정 */
        ClassLoader class_Loader;

        class_Loader = Thread.currentThread().getContextClassLoader();
        if (class_Loader == null) {
            class_Loader = ClassLoader.getSystemClassLoader();
        }
        URL url = class_Loader.getResource("config.properties");
        Properties config = new Properties();
        FileInputStream file_Input_Stream = null;
        try {
            file_Input_Stream = new FileInputStream(url.getFile());
            config.load(new BufferedInputStream(file_Input_Stream));

            ConfigureSetting.connectionTimeout = config.getProperty("connectionTimeout") == null ? 3000 : Integer.parseInt(config.getProperty("connectionTimeout"));
            ConfigureSetting.soTimeout = config.getProperty("soTimeout") == null ? 3000 : Integer.parseInt(config.getProperty("soTimeout"));


            ConfigureSetting.AUTHORIZATION_URL = config.getProperty("AUTH_URL") == null ? "" : "http://" + config.getProperty("AUTH_URL").trim();
            ConfigureSetting.AGENT_ID = config.getProperty("AGENT_ID") == null ? "" : config.getProperty("AGENT_ID").trim();
            ConfigureSetting.REQUEST_DATA = config.getProperty("REQUEST_DATA") == null ? "id" : config.getProperty("REQUEST_DATA").trim();

            ConfigureSetting.AUTH_LOGIN_PAGE = config.getProperty("AUTH_LOGIN_PAGE") == null ? "" : AUTHORIZATION_URL + config.getProperty("AUTH_LOGIN_PAGE").trim();
            ConfigureSetting.CHECK_SERVER_URL = config.getProperty("CHECK_SERVER_URL") == null ? "" : AUTHORIZATION_URL + config.getProperty("CHECK_SERVER_URL").trim();
            ConfigureSetting.TOKEN_AUTHORIZATION_URL = config.getProperty("TOKEN_AUTHORIZATION_URL") == null ? "" : AUTHORIZATION_URL + config.getProperty("TOKEN_AUTHORIZATION_URL").trim();
            ConfigureSetting.SAVE_TOKEN_URL = config.getProperty("SAVE_TOKEN_URL") == null ? "" : AUTHORIZATION_URL + config.getProperty("SAVE_TOKEN_URL").trim();
            ConfigureSetting.SERVICE_ERR_PAGE = config.getProperty("SERVICE_ERR_PAGE") == null ? "" : AUTHORIZATION_URL + config.getProperty("SERVICE_ERR_PAGE").trim();
            ConfigureSetting.AUTH_LOGOUT_PAGE = config.getProperty("AUTH_LOGOUT_PAGE") == null ? "" : AUTHORIZATION_URL + config.getProperty("AUTH_LOGOUT_PAGE").trim();


            ConfigureSetting.ERROR_PAGE = config.getProperty("ERROR_PAGE") == null ? "" : config.getProperty("ERROR_PAGE").trim();
            ConfigureSetting.LOGOUT_PAGE = config.getProperty("LOGOUT_PAGE") == null ? "" : config.getProperty("LOGOUT_PAGE").trim();
            ConfigureSetting.EXCEPTION_PAGE = config.getProperty("EXCEPTION_PAGE") == null ? "" : config.getProperty("EXCEPTION_PAGE").trim();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {file_Input_Stream.close();} catch(Exception e){}
        }

        return false;
    }
}