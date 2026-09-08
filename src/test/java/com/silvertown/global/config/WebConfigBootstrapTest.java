package com.silvertown.global.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class WebConfigBootstrapTest {

    @Test
    void registersVoiceStreamConfigurationWithTheServletContext() {
        WebConfig webConfig = new WebConfig();

        assertArrayEquals(
                new Class<?>[]{
                    ServletConfig.class, SwaggerConfig.class, VoiceStreamWebSocketConfig.class
                },
                webConfig.getServletConfigClasses());
    }
}
