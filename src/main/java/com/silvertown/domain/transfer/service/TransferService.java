package com.silvertown.domain.transfer.service;

import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareRequest;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferConfirmRequest;
import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.transfer.dto.TransferAuthenticationResponse;
import com.silvertown.domain.transfer.dto.TransferPinRequest;
import com.silvertown.domain.transfer.dto.TransferResultResponse;
import com.silvertown.domain.transfer.dto.GuardianVerificationStartRequest;
import com.silvertown.domain.transfer.dto.GuardianVerificationStartResponse;
import com.silvertown.domain.transfer.dto.GuardianVerificationConfirmRequest;
import com.silvertown.domain.transfer.dto.GuardianVerificationConfirmResponse;
import java.util.UUID;

public interface TransferService {
    AmountValidationResponse validateAmount(AmountValidationRequest request);

    TransferPrepareResponse prepare(UUID userId, TransferPrepareRequest request);

    TransferResponse get(UUID userId, UUID transferId);

    TransferResponse cancel(UUID userId, UUID transferId);

    void cancelUnexecuted(UUID userId, UUID transferId);

    TransferConfirmResponse confirm(UUID userId, UUID transferId, TransferConfirmRequest request);

    void registerOrChangePin(UUID userId, TransferPinRequest request);

    TransferAuthenticationResponse authenticate(
            UUID userId, UUID transferId, String confirmationToken, TransferPinRequest request);

    TransferResultResponse execute(
            UUID userId, UUID transferId, String confirmationToken, String idempotencyKey);

    GuardianVerificationStartResponse startGuardianVerification(UUID userId, UUID transferId,
            GuardianVerificationStartRequest request);

    GuardianVerificationConfirmResponse verifyGuardianVerification(UUID userId, UUID transferId,
            UUID verificationId, GuardianVerificationConfirmRequest request);

    String getDemoGuardianVerificationCode(UUID userId, UUID transferId, UUID verificationId);
}
