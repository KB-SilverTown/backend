package com.silvertown.domain.transfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.transfer.exception.GuardianVerificationStateException;
import com.silvertown.domain.transfer.mapper.TransferMapper;
import com.silvertown.domain.transfer.mms.MockMmsSender;
import com.silvertown.domain.transfer.service.impl.TransferServiceImpl;
import com.silvertown.domain.transfer.vo.TransferAuthentication;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.SensitiveDataHasher;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.annotation.Transactional;

class GuardianVerificationTransactionContractTest {

    @Test
    void commitsGuardianVerificationStateErrorsBeforeReturningApiError() throws Exception {
        Transactional transactional = TransferServiceImpl.class
                .getMethod("verifyGuardianVerification", UUID.class, UUID.class, UUID.class,
                        com.silvertown.domain.transfer.dto.GuardianVerificationConfirmRequest.class)
                .getAnnotation(Transactional.class);

        assertTrue(Arrays.asList(transactional.noRollbackFor())
                .contains(GuardianVerificationStateException.class));
    }

    @Test
    void exposesTransferAuthenticationIdentifier() {
        TransferAuthentication authentication = new TransferAuthentication();
        authentication.setTransferAuthenticationId("authentication-id");

        assertEquals("authentication-id", authentication.getTransferAuthenticationId());
    }

    @Test
    void rejectsGuardianVerificationStartWhenNoMmsDeliveryChannelIsAvailable() {
        TransferMapper mapper = Mockito.mock(TransferMapper.class);
        TransferService service = new TransferServiceImpl(null, null, mapper, null, null,
                new ObjectMapper(), Clock.systemUTC(), new SensitiveDataHasher("test-guardian-hmac-secret"),
                new MockMmsSender(false));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.startGuardianVerification(UUID.randomUUID(), UUID.randomUUID(), null));

        assertEquals(ErrorCode.GUARDIAN_DELIVERY_UNAVAILABLE, exception.getErrorCode());
        Mockito.verifyNoInteractions(mapper);
    }
}
