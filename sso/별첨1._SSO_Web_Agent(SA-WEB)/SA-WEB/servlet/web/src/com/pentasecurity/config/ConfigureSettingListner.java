package com.pentasecurity.config;

import com.pentasecurity.config.ConfigureSetting;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ConfigureSettingListner implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        ConfigureSetting.get_Configuration_Setting();
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {

    }
}

