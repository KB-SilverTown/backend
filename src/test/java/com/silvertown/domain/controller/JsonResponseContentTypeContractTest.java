package com.silvertown.domain.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.silvertown.domain.account.controller.AccountController;
import com.silvertown.domain.recipient.controller.RecipientController;
import com.silvertown.domain.risk.controller.RiskScoreController;
import com.silvertown.domain.transfer.controller.DemoGuardianVerificationController;
import com.silvertown.domain.transfer.controller.TransferController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;

class JsonResponseContentTypeContractTest {

    @Test
    void apiControllersThatPreviouslyNegotiatedXmlExplicitlyProduceJson() {
        assertJsonResponse(AccountController.class);
        assertJsonResponse(RecipientController.class);
        assertJsonResponse(RiskScoreController.class);
        assertJsonResponse(TransferController.class);
        assertJsonResponse(DemoGuardianVerificationController.class);
    }

    private void assertJsonResponse(Class<?> controllerType) {
        RequestMapping mapping = controllerType.getAnnotation(RequestMapping.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[] {MediaType.APPLICATION_JSON_VALUE}, mapping.produces());
    }
}
