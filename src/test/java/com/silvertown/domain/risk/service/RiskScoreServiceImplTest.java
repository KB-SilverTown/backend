package com.silvertown.domain.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.risk.blacklist.BlacklistResult;
import com.silvertown.domain.risk.dto.RiskCheckResponse;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.risk.mapper.RiskScoreMapper;
import com.silvertown.domain.risk.service.impl.RiskScoreServiceImpl;
import com.silvertown.domain.risk.vo.RiskAssessment;
import com.silvertown.domain.risk.vo.RiskCheck;
import com.silvertown.domain.transfer.mapper.TransferMapper;
import com.silvertown.domain.transfer.vo.TransferAuthentication;
import com.silvertown.domain.transfer.vo.TransferConfirmation;
import com.silvertown.domain.transfer.vo.TransferTransaction;
import com.silvertown.domain.transfer.vo.Transfer;

import com.silvertown.domain.transfer.vo.TransferAuthentication;
import com.silvertown.domain.transfer.vo.TransferConfirmation;
import com.silvertown.domain.transfer.vo.TransferTransaction;
import com.silvertown.domain.transfer.vo.UserTransferPin;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskScoreServiceImplTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRANSFER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-02T06:00:00Z"), ZoneId.of("Asia/Seoul"));
    private FakeTransferMapper transferMapper;
    private FakeRiskScoreMapper riskMapper;
    private AccountNumberCrypto crypto;

    @BeforeEach
    void setUp() {
        transferMapper = new FakeTransferMapper();
        riskMapper = new FakeRiskScoreMapper();
        crypto = new AccountNumberCrypto(
                Base64.getEncoder().encodeToString(new byte[32]));
        transferMapper.transfer = transfer("DRAFT", 100_000L);
        riskMapper.lastSuccessful = OffsetDateTime.now(clock).minusDays(30);
    }

    @Test
    void blacklistMatchUsesSameAdditionalCheckValueForStorageAndResponse() {
        RiskScoreService service = service("MATCH");

        RiskScoreResponse response = service.assess(USER_ID, TRANSFER_ID);

        assertTrue(response.isAdditionalCheckRequired());
        assertTrue(riskMapper.savedAssessment.isAdditionalCheckRequired());
        assertEquals("CRITICAL", riskMapper.savedAssessment.getLevel());
        assertEquals(1, riskMapper.holdCalls);
    }

    @Test
    void failedHoldTransitionReturnsInvalidStateInsteadOfClaimingHold() {
        riskMapper.holdResult = 0;
        transferMapper.afterFailedHold = transfer("EXECUTED", 100_000L);
        RiskScoreService service = service("MATCH");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.assess(USER_ID, TRANSFER_ID));

        assertEquals(ErrorCode.TRANSFER_INVALID_STATE, exception.getErrorCode());
    }

    @Test
    void purposeAnswerIsStoredWithoutReplacingItWithRiskCode() {
        RiskScoreService service = service("NO_MATCH");
        String answer = "대출받기 전에 수수료를 먼저 입금해야 한대요";

        service.checkContext(USER_ID, TRANSFER_ID, answer);

        assertEquals(answer, riskMapper.savedCheck.getPurposeAnswer());
    }

    @Test
    void safeMediumPurposeAnswerClearsAdditionalCheckRequirement() {
        transferMapper.transfer = transfer("DRAFT", 500_000L);
        riskMapper.lastSuccessful = null;
        RiskScoreService service = service("NO_MATCH");

        RiskCheckResponse response = service.checkContext(USER_ID, TRANSFER_ID, "생활비를 보내려고 합니다.");

        assertEquals(30, response.getScore());
        assertTrue(!response.isSuspicious());
        assertTrue(!response.isAdditionalCheckRequired());
        assertTrue(!riskMapper.savedAssessment.isAdditionalCheckRequired());
    }

    @Test
    void transactionRiskCanBeSuspiciousWithoutConversationFactor() {
        transferMapper.transfer = transfer("DRAFT", 10_000_000L);
        riskMapper.lastSuccessful = null;
        riskMapper.distinctRecipients = 3;
        RiskScoreService service = service("NO_MATCH");

        RiskCheckResponse response = service.checkContext(USER_ID, TRANSFER_ID, null);

        assertTrue(response.isSuspicious());
        assertTrue(response.isHold());
        assertTrue(riskMapper.savedCheck.isSuspicious());
    }

    @Test
    void alreadyHeldTransferKeepsHoldInResponseAndStoredCheckAfterScoreDrops() {
        transferMapper.transfer = transfer("HELD", 100_000L);
        RiskScoreService service = service("NO_MATCH");

        RiskCheckResponse response = service.checkContext(USER_ID, TRANSFER_ID, null);

        assertTrue(response.isHold());
        assertTrue(response.isSuspicious());
        assertTrue(riskMapper.savedCheck.isHold());
        assertEquals(0, riskMapper.holdCalls);
    }

    @Test
    void alreadyHeldTransferKeepsHoldActionInScoreResponse() {
        transferMapper.transfer = transfer("HELD", 100_000L);
        RiskScoreService service = service("NO_MATCH");

        RiskScoreResponse response = service.assess(USER_ID, TRANSFER_ID);

        assertEquals("HOLD", response.getRecommendedAction());
        assertTrue(response.isAdditionalCheckRequired());
        assertTrue(riskMapper.savedAssessment.isAdditionalCheckRequired());
    }

    @Test
    void mediumRiskWithSafePurposeCanProceedToReadbackAfterAdditionalCheck() {
        transferMapper.transfer = transfer("DRAFT", 3_000_000L);
        riskMapper.attempts = 2;
        RiskScoreService service = service("NO_MATCH");

        RiskScoreResponse assessed = service.assess(USER_ID, TRANSFER_ID);
        RiskCheckResponse checked = service.checkContext(USER_ID, TRANSFER_ID, "생활비를 보내는 거예요");

        assertEquals("MEDIUM", assessed.getLevel());
        assertTrue(assessed.isAdditionalCheckRequired());
        assertEquals("MEDIUM", checked.getLevel());
        assertTrue(!checked.isSuspicious());
        assertTrue(!checked.isAdditionalCheckRequired());
        assertTrue(!riskMapper.savedAssessment.isAdditionalCheckRequired());
    }

    private RiskScoreService service(String blacklistStatus) {
        return new RiskScoreServiceImpl(
                transferMapper,
                riskMapper,
                new RiskPolicyCalculator(),
                (bankCode, accountNumber) -> new BlacklistResult("MOCK", blacklistStatus),
                crypto,
                new ObjectMapper(),
                clock);
    }

    private Transfer transfer(String status, long amount) {
        Transfer transfer = new Transfer();
        transfer.setTransferId(TRANSFER_ID.toString());
        transfer.setUserId(USER_ID.toString());
        transfer.setRecipientId("20000000-0000-0000-0000-000000000001");
        transfer.setStatus(status);
        transfer.setAmount(amount);
        transfer.setPreparedAt(OffsetDateTime.now(clock));
        transfer.setRecipientBankCode("004");
        transfer.setRecipientAccountNumberEncrypted(crypto.encrypt("1234567890"));
        return transfer;
    }

    private static final class FakeTransferMapper implements TransferMapper {
        private Transfer transfer;
        private Transfer afterFailedHold;

        @Override
        public String findOwnedVoiceSessionIdForUpdate(String userId, String sessionId) {
            return null;
        }

        @Override
        public int insert(Transfer transfer) {
            return 0;
        }

        @Override
        public Transfer findOwnedById(String userId, String transferId) {
            return afterFailedHold == null ? transfer : afterFailedHold;
        }

        @Override
        public Transfer findOwnedByIdForUpdate(String userId, String transferId) {
            return transfer;
        }

        @Override
        public Transfer findOwnedDraftByVoiceSession(String userId, String sessionId) {
            return null;
        }

        @Override
        public int cancelIfExecutable(String userId, String transferId) {
            return 0;
        }

        @Override
        public int cancelUnexecutedIfPending(String userId, String transferId) {
            return 0;
        }

        @Override
        public Boolean findLatestAdditionalCheckRequired(String transferId) {
            return false;
        }

        @Override
        public int confirmIfRiskChecked(String userId, String transferId,
                String confirmationTokenHash, OffsetDateTime confirmationTokenExpiresAt) {
            return 0;
        }

        @Override
        public int refreshConfirmationToken(String userId, String transferId,
                String confirmationTokenHash, OffsetDateTime confirmationTokenExpiresAt) {
            return 0;
        }

        @Override
        public int insertConfirmation(TransferConfirmation confirmation) {
            return 0;
        }

        @Override
        public UserTransferPin findPinForUpdate(String userId) {
            return null;
        }

        @Override
        public int upsertPin(String userId, String pinHash) {
            return 0;
        }

        @Override
        public int recordPinFailure(String userId, OffsetDateTime lockedUntil) {
            return 0;
        }

        @Override
        public int resetPinFailures(String userId) {
            return 0;
        }

        @Override
        public int expireAuthenticatedAuthentications(String userId, String transferId) {
            return 0;
        }

        @Override
        public int insertAuthentication(TransferAuthentication authentication) {
            return 0;
        }

        @Override
        public TransferAuthentication findLatestAuthenticationForUpdate(String userId, String transferId) {
            return null;
        }

        @Override
        public int consumeAuthentication(String transferAuthenticationId) {
            return 0;
        }

        @Override
        public TransferTransaction findTransactionByIdempotencyKey(String userId, String idempotencyKey) {
            return null;
        }

        @Override
        public int insertTransaction(TransferTransaction transaction) {
            return 0;
        }

        @Override
        public int executeIfConfirmed(String userId, String transferId) {
            return 0;
        }

        @Override
        public String findEmergencyContactPhoneHash(String userId) {
            return null;
        }

        @Override
        public int countGuardianDeliveryAttempts(String transferId) {
            return 0;
        }

        @Override
        public com.silvertown.domain.transfer.vo.GuardianVerification
                findLatestGuardianDeliveryForUpdate(String transferId) {
            return null;
        }

        @Override
        public com.silvertown.domain.transfer.vo.GuardianVerification
                findActiveGuardianVerificationForUpdate(String transferId) {
            return null;
        }

        @Override
        public com.silvertown.domain.transfer.vo.GuardianVerification
                findGuardianVerificationForUpdate(String userId, String transferId, String verificationId) {
            return null;
        }

        @Override
        public int insertGuardianVerification(
                com.silvertown.domain.transfer.vo.GuardianVerification verification) {
            return 0;
        }

        @Override
        public int incrementGuardianVerificationAttempts(String verificationId) {
            return 0;
        }

        @Override
        public int expireGuardianVerification(String verificationId) {
            return 0;
        }

        @Override
        public int failGuardianVerification(String verificationId) {
            return 0;
        }

        @Override
        public int verifyGuardianVerification(String verificationId, OffsetDateTime verifiedAt) {
            return 0;
        }

        @Override
        public int reconfirmAfterGuardianVerification(String userId, String transferId) {
            return 0;
        }
    }

    private static final class FakeRiskScoreMapper implements RiskScoreMapper {
        private OffsetDateTime lastSuccessful;
        private int attempts = 1;
        private int distinctRecipients;
        private int holdResult = 1;
        private int holdCalls;
        private RiskAssessment savedAssessment;
        private RiskCheck savedCheck;

        @Override
        public OffsetDateTime findLastSuccessfulTransferAt(String userId, String recipientId) {
            return lastSuccessful;
        }

        @Override
        public List<Long> findRecentUserAmounts(String userId) {
            return List.of();
        }

        @Override
        public List<Long> findRecentRecipientAmounts(String userId, String recipientId) {
            return List.of();
        }

        @Override
        public int countRecentAttempts(String userId, String recipientId) {
            return attempts;
        }

        @Override
        public int countRecentDistinctRecipients(String userId) {
            return distinctRecipients;
        }

        @Override
        public int insertAssessment(RiskAssessment assessment) {
            savedAssessment = assessment;
            return 1;
        }

        @Override
        public int insertRiskCheck(RiskCheck riskCheck) {
            savedCheck = riskCheck;
            return 1;
        }

        @Override
        public int holdTransfer(String userId, String transferId) {
            holdCalls++;
            return holdResult;
        }
    }
}
