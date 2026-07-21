package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class LogbackFilePathConfigTest {

    @Test
    @DisplayName("미열거 프로파일에서도 사용할 기본 로그 경로가 최상위에 정의된다")
    void defaultLogPath_isDefinedOutsideProfiles() throws Exception {
        try (InputStream config = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertThat(config).isNotNull();

            Document document =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(config);
            Element configuration = document.getDocumentElement();
            NodeList children = configuration.getChildNodes();

            String defaultLogPath = null;
            for (int index = 0; index < children.getLength(); index++) {
                Node child = children.item(index);
                if (child instanceof Element element
                        && "property".equals(element.getTagName())
                        && "LOG_PATH".equals(element.getAttribute("name"))) {
                    defaultLogPath = element.getAttribute("value");
                    break;
                }
            }

            assertThat(defaultLogPath).isEqualTo("c:/itp_log");
        }
    }
}
