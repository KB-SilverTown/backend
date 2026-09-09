package com.silvertown.domain.transfer.mapper;

import com.silvertown.domain.transfer.vo.Transfer;
import com.silvertown.domain.transfer.vo.TransferConfirmation;
import com.silvertown.domain.transfer.vo.TransferAuthentication;
import com.silvertown.domain.transfer.vo.TransferTransaction;
import com.silvertown.domain.transfer.vo.UserTransferPin;
import com.silvertown.domain.transfer.vo.GuardianVerification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TransferMapper {
    String findOwnedVoiceSessionIdForUpdate(
            @Param("userId") String userId, @Param("sessionId") String sessionId);

    int insert(Transfer transfer);

    Transfer findOwnedById(
            @Param("userId") String userId, @Param("transferId") String transferId);

    Transfer findOwnedByIdForUpdate(
            @Param("userId") String userId, @Param("transferId") String transferId);

    Transfer findOwnedDraftByVoiceSession(
            @Param("userId") String userId, @Param("sessionId") String sessionId);

    int cancelIfExecutable(
            @Param("userId") String userId, @Param("transferId") String transferId);

    int cancelUnexecutedIfPending(
            @Param("userId") String userId, @Param("transferId") String transferId);

    Boolean findLatestAdditionalCheckRequired(@Param("transferId") String transferId);

    int confirmIfRiskChecked(
            @Param("userId") String userId, @Param("transferId") String transferId,
            @Param("confirmationTokenHash") String confirmationTokenHash,
            @Param("confirmationTokenExpiresAt") java.time.OffsetDateTime confirmationTokenExpiresAt);

    int refreshConfirmationToken(
            @Param("userId") String userId, @Param("transferId") String transferId,
            @Param("confirmationTokenHash") String confirmationTokenHash,
            @Param("confirmationTokenExpiresAt") java.time.OffsetDateTime confirmationTokenExpiresAt);

    int insertConfirmation(TransferConfirmation confirmation);

    UserTransferPin findPinForUpdate(@Param("userId") String userId);
    int upsertPin(@Param("userId") String userId, @Param("pinHash") String pinHash);
    int recordPinFailure(@Param("userId") String userId, @Param("lockedUntil") java.time.OffsetDateTime lockedUntil);
    int resetPinFailures(@Param("userId") String userId);

    int expireAuthenticatedAuthentications(@Param("userId") String userId, @Param("transferId") String transferId);
    int insertAuthentication(TransferAuthentication authentication);
    TransferAuthentication findLatestAuthenticationForUpdate(@Param("userId") String userId, @Param("transferId") String transferId);
    int consumeAuthentication(
            @Param("transferAuthenticationId") String transferAuthenticationId);

    TransferTransaction findTransactionByIdempotencyKey(@Param("userId") String userId, @Param("idempotencyKey") String idempotencyKey);
    int insertTransaction(TransferTransaction transaction);
    int executeIfConfirmed(@Param("userId") String userId, @Param("transferId") String transferId);

    String findEmergencyContactPhoneHash(@Param("userId") String userId);
    int countGuardianDeliveryAttempts(@Param("transferId") String transferId);
    GuardianVerification findLatestGuardianDeliveryForUpdate(@Param("transferId") String transferId);
    GuardianVerification findActiveGuardianVerificationForUpdate(@Param("transferId") String transferId);
    GuardianVerification findGuardianVerificationForUpdate(@Param("userId") String userId,
            @Param("transferId") String transferId, @Param("verificationId") String verificationId);
    int insertGuardianVerification(GuardianVerification verification);
    int incrementGuardianVerificationAttempts(@Param("verificationId") String verificationId);
    int expireGuardianVerification(@Param("verificationId") String verificationId);
    int failGuardianVerification(@Param("verificationId") String verificationId);
    int verifyGuardianVerification(@Param("verificationId") String verificationId,
            @Param("verifiedAt") java.time.OffsetDateTime verifiedAt);
    int reconfirmAfterGuardianVerification(@Param("userId") String userId, @Param("transferId") String transferId);
}
