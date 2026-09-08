package com.silvertown.global.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class WebConfigBootstrapTest {

    @Test
    void excludesVoiceStreamConfigurationUntilVoiceIntegration() {
        WebConfig webConfig = new WebConfig();

        assertArrayEquals(
                new Class<?>[]{ServletConfig.class, SwaggerConfig.class},
                webConfig.getServletConfigClasses());
    }
}
