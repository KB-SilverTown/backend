package com.silvertown.global.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartException;

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

    @Test
    void usesOperatingSystemTemporaryDirectoryForMultipartUploads() {
        assertEquals(System.getProperty("java.io.tmpdir"), WebConfig.UPLOAD_TEMP_DIRECTORY);
    }

    @Test
    void mapsMultipartExceptionToBillImageInvalidResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = new GlobalExceptionHandler().handleMultipartException(
                new MultipartException("temporary upload directory is invalid"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.BILL_IMAGE_INVALID.getCode(), response.getBody().getCode());
    }
}
