package com.silvertown.domain.transfer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.account.vo.BankAccount;
import com.silvertown.domain.recipient.mapper.RecipientMapper;
import com.silvertown.domain.recipient.vo.Recipient;
import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareRequest;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferConfirmRequest;
import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import com.silvertown.domain.transfer.dto.TransferRecipientResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.transfer.dto.TransferPinRequest;
import com.silvertown.domain.transfer.dto.TransferAuthenticationResponse;
import com.silvertown.domain.transfer.dto.TransferResultResponse;
import com.silvertown.domain.transfer.dto.GuardianVerificationStartRequest;
import com.silvertown.domain.transfer.dto.GuardianVerificationStartResponse;
import com.silvertown.domain.transfer.dto.GuardianVerificationConfirmRequest;
import com.silvertown.domain.transfer.dto.GuardianVerificationConfirmResponse;
import com.silvertown.domain.transfer.mms.MmsSendResult;
import com.silvertown.domain.transfer.mms.MmsSender;
import com.silvertown.domain.transfer.mms.MockMmsSender;
import com.silvertown.domain.transfer.vo.GuardianVerification;
import com.silvertown.domain.transfer.vo.UserTransferPin;
import com.silvertown.domain.transfer.vo.TransferAuthentication;
import com.silvertown.domain.transfer.vo.TransferTransaction;
import com.silvertown.domain.transfer.mapper.TransferMapper;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.transfer.vo.Transfer;
import com.silvertown.domain.transfer.vo.TransferConfirmation;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.domain.transfer.exception.GuardianVerificationStateException;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import com.silvertown.global.security.crypto.AccountNumberMasker;
import com.silvertown.global.security.SensitiveDataHasher;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

@Service
public class TransferServiceImpl implements TransferService {
    private static final String DRAFT = "DRAFT";
    private static final String CANCELLED = "CANCELLED";
    private static final String RISK_CHECK = "RISK_CHECK";
    private static final int PIN_MAX_FAILURES = 5;
    private static final int GUARDIAN_MAX_ATTEMPTS = 5;
    private static final int GUARDIAN_MAX_DELIVERY_ATTEMPTS = 3;
    private static final java.time.Duration GUARDIAN_RESEND_COOLDOWN = java.time.Duration.ofSeconds(60);
    private static final java.time.Duration GUARDIAN_VERIFICATION_DURATION = java.time.Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final java.time.Duration PIN_LOCK_DURATION = java.time.Duration.ofMinutes(30);
    private static final java.time.Duration AUTHENTICATION_DURATION = java.time.Duration.ofMinutes(5);
    private static final java.time.Duration CONFIRMATION_TOKEN_DURATION = java.time.Duration.ofMinutes(5);
    private static final BCryptPasswordEncoder PIN_ENCODER = new BCryptPasswordEncoder();
    private static final Pattern ARABIC_AMOUNT_PATTERN = Pattern.compile(
            "(?<![0-9])([0-9][0-9,]*)(?:\\s*(억|만|천))?\\s*원");
    private static final Pattern KOREAN_AMOUNT_PATTERN = Pattern.compile(
            "([일이삼사오육칠팔구영공십백천만억]+)\\s*원");

    private final AccountMapper accountMapper;
    private final RecipientMapper recipientMapper;
    private final TransferMapper transferMapper;
    private final AccountNumberCrypto crypto;
    private final AccountNumberMasker masker;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SensitiveDataHasher sensitiveDataHasher;
    private final MmsSender mmsSender;
    private final MockMmsSender mockMmsSender;

    @Autowired
    public TransferServiceImpl(AccountMapper accountMapper, RecipientMapper recipientMapper,
            TransferMapper transferMapper, AccountNumberCrypto crypto, AccountNumberMasker masker,
            ObjectMapper objectMapper, Clock clock, SensitiveDataHasher sensitiveDataHasher,
            MmsSender mmsSender) {
        this.accountMapper = accountMapper;
        this.recipientMapper = recipientMapper;
        this.transferMapper = transferMapper;
        this.crypto = crypto;
        this.masker = masker;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.sensitiveDataHasher = sensitiveDataHasher;
        this.mmsSender = mmsSender;
        this.mockMmsSender = mmsSender instanceof MockMmsSender
                ? (MockMmsSender) mmsSender : null;
    }

    @Override
    public AmountValidationResponse validateAmount(AmountValidationRequest request) {
        if (request == null) {
            throw invalidAmount();
        }
        Long recognized = request.getRecognizedAmount();
        boolean transcriptProvided = request.getTranscript() != null && !request.getTranscript().isBlank();
        List<Long> candidates = !transcriptProvided
                ? request.getAmountCandidates() == null
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(new ArrayList<>(request.getAmountCandidates()))
                : extractAmountCandidates(request.getTranscript());
        if (!transcriptProvided && (hasInvalidCandidates(candidates)
                || hasMismatchedRecognizedAmount(recognized, candidates))) {
            throw invalidAmount();
        }
        if ((recognized != null && recognized <= 0)
                || candidates.stream().anyMatch(candidate -> candidate == null || candidate <= 0)) {
            throw invalidAmount();
        }
        boolean reconfirmRequired = candidates.size() != 1
                || recognized == null
                || !Objects.equals(recognized, candidates.get(0));
        return new AmountValidationResponse(
                recognized, candidates, reconfirmRequired ? null : recognized, reconfirmRequired);
    }

    private boolean hasInvalidCandidates(List<Long> candidates) {
        if (candidates.isEmpty() || candidates.size() > 3) return true;
        if (candidates.stream().anyMatch(candidate -> candidate == null || candidate <= 0)) return true;
        return new java.util.HashSet<>(candidates).size() != candidates.size();
    }

    private boolean hasMismatchedRecognizedAmount(Long recognized, List<Long> candidates) {
        return recognized != null
                && (candidates.size() != 1 || !Objects.equals(recognized, candidates.get(0)));
    }

    private List<Long> extractAmountCandidates(String transcript) {
        List<Long> candidates = new ArrayList<>();
        Matcher arabicMatcher = ARABIC_AMOUNT_PATTERN.matcher(transcript);
        while (arabicMatcher.find()) {
            long amount = scaledArabicAmount(arabicMatcher.group(1), arabicMatcher.group(2));
            addCandidate(candidates, amount);
        }
        Matcher koreanMatcher = KOREAN_AMOUNT_PATTERN.matcher(transcript);
        while (koreanMatcher.find()) {
            addCandidate(candidates, parseKoreanAmount(koreanMatcher.group(1)));
        }
        return Collections.unmodifiableList(candidates);
    }

    private long scaledArabicAmount(String digits, String unit) {
        long amount;
        try {
            amount = Long.parseLong(digits.replace(",", ""));
        } catch (NumberFormatException exception) {
            return -1;
        }
        if ("천".equals(unit)) return safeMultiply(amount, 1_000L);
        if ("만".equals(unit)) return safeMultiply(amount, 10_000L);
        if ("억".equals(unit)) return safeMultiply(amount, 100_000_000L);
        return amount;
    }

    private long parseKoreanAmount(String text) {
        long total = 0;
        long section = 0;
        long number = 0;
        for (int index = 0; index < text.length(); index++) {
            int digit = koreanDigit(text.charAt(index));
            if (digit >= 0) {
                number = digit;
                continue;
            }
            long unit = koreanUnit(text.charAt(index));
            if (unit < 10_000L) {
                section += (number == 0 ? 1 : number) * unit;
                number = 0;
            } else {
                long sectionValue = section + number;
                total += safeMultiply(sectionValue == 0 ? 1 : sectionValue, unit);
                section = 0;
                number = 0;
            }
        }
        return total + section + number;
    }

    private void addCandidate(List<Long> candidates, long amount) {
        if (amount > 0 && !candidates.contains(amount)) candidates.add(amount);
    }

    private long safeMultiply(long value, long multiplier) {
        return value > Long.MAX_VALUE / multiplier ? -1 : value * multiplier;
    }

    private int koreanDigit(char character) {
        return switch (character) {
            case '일' -> 1;
            case '이' -> 2;
            case '삼' -> 3;
            case '사' -> 4;
            case '오' -> 5;
            case '육' -> 6;
            case '칠' -> 7;
            case '팔' -> 8;
            case '구' -> 9;
            case '영', '공' -> 0;
            default -> -1;
        };
    }

    private long koreanUnit(char character) {
        return switch (character) {
            case '십' -> 10L;
            case '백' -> 100L;
            case '천' -> 1_000L;
            case '만' -> 10_000L;
            case '억' -> 100_000_000L;
            default -> -1L;
        };
    }

    @Override
    @Transactional
    public TransferPrepareResponse prepare(UUID userId, TransferPrepareRequest request) {
        validatePositive(request.getAmount());
        BankAccount account = accountMapper.findOwnedActiveById(
                userId.toString(), request.getFromAccountId().toString());
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (account.getBalance() < request.getAmount()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        Recipient recipient = recipientMapper.findOwnedById(
                userId.toString(), request.getRecipientId().toString());
        if (recipient == null) {
            throw new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
       if (request.getVoiceSessionId() != null
        && transferMapper.findOwnedVoiceSessionIdForUpdate(
                userId.toString(), request.getVoiceSessionId().toString()) == null) {
    throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
}
        Transfer existingDraft = request.getVoiceSessionId() == null
                ? null
                : transferMapper.findOwnedDraftByVoiceSession(
                        userId.toString(), request.getVoiceSessionId().toString());
        if (existingDraft != null) {
            if (!samePreparation(existingDraft, request)) {
                throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
            }
            return new TransferPrepareResponse(
                    UUID.fromString(existingDraft.getTransferId()), existingDraft.getStatus(),
                    existingDraft.getCurrentStep(), toRecipientResponse(recipient), existingDraft.getAmount(),
                    existingDraft.isAmountReconfirmRequired(),
                    confirmationText(recipient.getDisplayName(), existingDraft.getAmount()),
                    existingDraft.getPreparedAt());
        }

        Transfer transfer = new Transfer();
        transfer.setTransferId(UUID.randomUUID().toString());
        transfer.setUserId(userId.toString());
        transfer.setSessionId(request.getVoiceSessionId() == null
                ? null
                : request.getVoiceSessionId().toString());
        transfer.setFromAccountId(request.getFromAccountId().toString());
        transfer.setRecipientId(request.getRecipientId().toString());
        transfer.setAmount(request.getAmount());
        transfer.setStatus(DRAFT);
        transfer.setCurrentStep(RISK_CHECK);
        transfer.setAmountCandidates("[]");
        transfer.setPreparedAt(OffsetDateTime.now(clock));
        transferMapper.insert(transfer);

        TransferRecipientResponse recipientResponse = toRecipientResponse(recipient);
        return new TransferPrepareResponse(
                UUID.fromString(transfer.getTransferId()), DRAFT, RISK_CHECK, recipientResponse,
                transfer.getAmount(), false, confirmationText(recipient.getDisplayName(), transfer.getAmount()),
                transfer.getPreparedAt());
    }

    private boolean samePreparation(Transfer transfer, TransferPrepareRequest request) {
        return request.getFromAccountId().toString().equals(transfer.getFromAccountId())
                && request.getRecipientId().toString().equals(transfer.getRecipientId())
                && request.getAmount().equals(transfer.getAmount());
    }

    @Override
    @Transactional(readOnly = true)
    public TransferResponse get(UUID userId, UUID transferId) {
        return toResponse(findOwned(userId, transferId));
    }

    @Override
    @Transactional
    public TransferResponse cancel(UUID userId, UUID transferId) {
        Transfer transfer = transferMapper.findOwnedByIdForUpdate(
                userId.toString(), transferId.toString());
        if (transfer == null) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
        }
        if (CANCELLED.equals(transfer.getStatus())) {
            return toResponse(transfer);
        }
        if ("EXECUTED".equals(transfer.getStatus()) || "EXPIRED".equals(transfer.getStatus())) {
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        if (transferMapper.cancelIfExecutable(userId.toString(), transferId.toString()) == 0) {
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        return toResponse(findOwned(userId, transferId));
    }

    @Override
    @Transactional
    public void cancelUnexecuted(UUID userId, UUID transferId) {
        transferMapper.cancelUnexecutedIfPending(userId.toString(), transferId.toString());
    }

    @Override
    @Transactional
    public TransferConfirmResponse confirm(
            UUID userId, UUID transferId, TransferConfirmRequest request) {
        Transfer transfer = transferMapper.findOwnedByIdForUpdate(
                userId.toString(), transferId.toString());
        if (transfer == null) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
        }
        if ("CONFIRMED".equals(transfer.getStatus())) {
            if (!request.getApproved()) {
                throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
            }
            ConfirmationToken confirmationToken = issueConfirmationToken();
            if (transferMapper.refreshConfirmationToken(userId.toString(), transferId.toString(),
                    confirmationToken.hash(), confirmationToken.expiresAt()) != 1) {
                throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
            }
            transfer.setConfirmationTokenHash(confirmationToken.hash());
            transfer.setConfirmationTokenExpiresAt(confirmationToken.expiresAt());
            return confirmResponse(transfer, true, confirmationToken.value());
        }
        if ("HELD".equals(transfer.getStatus()) || "EXECUTED".equals(transfer.getStatus())
                || "CANCELLED".equals(transfer.getStatus()) || "EXPIRED".equals(transfer.getStatus())) {
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        Boolean additionalCheckRequired = transferMapper.findLatestAdditionalCheckRequired(
                transferId.toString());
        if (additionalCheckRequired == null
                || (additionalCheckRequired && !"RECONFIRM".equals(transfer.getStatus()))) {
            throw new BusinessException(ErrorCode.RISK_CHECK_REQUIRED);
        }
        if (!request.getApproved()) {
            if (transferMapper.cancelIfExecutable(userId.toString(), transferId.toString()) != 1) {
                throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
            }
            insertConfirmation(transfer, false);
            transfer.setStatus(CANCELLED);
            transfer.setCurrentStep(CANCELLED);
            return confirmResponse(transfer, false, null);
        }
        ConfirmationToken confirmationToken = issueConfirmationToken();
        if (transferMapper.confirmIfRiskChecked(userId.toString(), transferId.toString(),
                confirmationToken.hash(), confirmationToken.expiresAt()) != 1) {
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        insertConfirmation(transfer, true);
        transfer.setStatus("CONFIRMED");
        transfer.setCurrentStep("AUTHENTICATE");
        transfer.setApprovedAt(OffsetDateTime.now(clock));
        transfer.setConfirmationTokenHash(confirmationToken.hash());
        transfer.setConfirmationTokenExpiresAt(confirmationToken.expiresAt());
        return confirmResponse(transfer, true, confirmationToken.value());
    }

    @Override
    @Transactional
    public GuardianVerificationStartResponse startGuardianVerification(UUID userId, UUID transferId,
            GuardianVerificationStartRequest request) {
        if (mmsSender == null || !mmsSender.isAvailable()) {
            throw new BusinessException(ErrorCode.GUARDIAN_DELIVERY_UNAVAILABLE);
        }
        Transfer transfer = transferMapper.findOwnedByIdForUpdate(userId.toString(), transferId.toString());
        if (transfer == null) throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
        if (!"HELD".equals(transfer.getStatus())) throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        OffsetDateTime now = OffsetDateTime.now(clock);
        GuardianVerification latestDelivery = transferMapper.findLatestGuardianDeliveryForUpdate(
                transferId.toString());
        if (latestDelivery != null && latestDelivery.getSentAt() != null
                && latestDelivery.getSentAt().plus(GUARDIAN_RESEND_COOLDOWN).isAfter(now)) {
            throw new BusinessException(ErrorCode.GUARDIAN_RESEND_NOT_AVAILABLE);
        }
        GuardianVerification active = transferMapper.findActiveGuardianVerificationForUpdate(transferId.toString());
        if (active != null) {
            transferMapper.expireGuardianVerification(active.getVerificationId());
        }
        String phoneHash = transferMapper.findEmergencyContactPhoneHash(userId.toString());
        if (phoneHash == null || phoneHash.isBlank()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (transferMapper.countGuardianDeliveryAttempts(transferId.toString())
                >= GUARDIAN_MAX_DELIVERY_ATTEMPTS) {
            throw new BusinessException(ErrorCode.GUARDIAN_DELIVERY_ATTEMPT_EXCEEDED);
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        GuardianVerification verification = new GuardianVerification();
        verification.setVerificationId(UUID.randomUUID().toString());
        verification.setTransferId(transferId.toString());
        verification.setTargetPhoneHash(phoneHash);
        verification.setChannel("MMS");
        verification.setStatus("SENT");
        verification.setVerificationCodeHash(hashVerificationCode(code));
        verification.setAttemptCount(0);
        verification.setMaxAttempts(GUARDIAN_MAX_ATTEMPTS);
        verification.setSentAt(now);
        verification.setExpiresAt(now.plus(GUARDIAN_VERIFICATION_DURATION));
        MmsSendResult delivery = sendWithOneTemporaryRetry(
                UUID.fromString(verification.getVerificationId()), code, now);
        if (!delivery.delivered()
                && delivery.failureCode() == MmsSendResult.FailureCode.MMS_TEMPORARY_FAILURE) {
            throw new BusinessException(ErrorCode.GUARDIAN_DELIVERY_UNAVAILABLE);
        }
        if (!delivery.delivered()) {
            verification.setStatus("FAILED");
            verification.setFailedAt(now);
        }
        transferMapper.insertGuardianVerification(verification);
        return new GuardianVerificationStartResponse(UUID.fromString(verification.getVerificationId()),
                verification.getStatus(), verification.getExpiresAt(),
                delivery.failureCode() == null ? null : delivery.failureCode().name());
    }

    @Override
    @Transactional(noRollbackFor = GuardianVerificationStateException.class)
    public GuardianVerificationConfirmResponse verifyGuardianVerification(UUID userId, UUID transferId,
            UUID verificationId, GuardianVerificationConfirmRequest request) {
        Transfer transfer = transferMapper.findOwnedByIdForUpdate(userId.toString(), transferId.toString());
        if (transfer == null) throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
        if (!"HELD".equals(transfer.getStatus())) throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        GuardianVerification verification = transferMapper.findGuardianVerificationForUpdate(
                userId.toString(), transferId.toString(), verificationId.toString());
        if (verification == null) throw new BusinessException(ErrorCode.GUARDIAN_VERIFICATION_NOT_FOUND);
        if (!"SENT".equals(verification.getStatus())) {
            if ("EXPIRED".equals(verification.getStatus())) throw new BusinessException(ErrorCode.GUARDIAN_CODE_EXPIRED);
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!verification.getExpiresAt().isAfter(now)) {
            transferMapper.expireGuardianVerification(verification.getVerificationId());
            throw guardianVerificationStateError(ErrorCode.GUARDIAN_CODE_EXPIRED);
        }
        if (verification.getAttemptCount() >= verification.getMaxAttempts()) {
            transferMapper.failGuardianVerification(verification.getVerificationId());
            throw guardianVerificationStateError(ErrorCode.GUARDIAN_ATTEMPT_EXCEEDED);
        }
        if (!hashVerificationCode(request.getCode()).equals(verification.getVerificationCodeHash())) {
            transferMapper.incrementGuardianVerificationAttempts(verification.getVerificationId());
            if (verification.getAttemptCount() + 1 >= verification.getMaxAttempts()) {
                transferMapper.failGuardianVerification(verification.getVerificationId());
                throw guardianVerificationStateError(ErrorCode.GUARDIAN_ATTEMPT_EXCEEDED);
            }
            throw guardianVerificationStateError(ErrorCode.GUARDIAN_CODE_INVALID);
        }
        if (transferMapper.verifyGuardianVerification(verification.getVerificationId(), now) != 1
                || transferMapper.reconfirmAfterGuardianVerification(userId.toString(), transferId.toString()) != 1) {
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        return new GuardianVerificationConfirmResponse(true, "RECONFIRM", now);
    }

    @Override
    @Transactional
    public String getDemoGuardianVerificationCode(UUID userId, UUID transferId, UUID verificationId) {
        findOwned(userId, transferId);
        if (mockMmsSender == null || !mockMmsSender.isDemoInboxEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        GuardianVerification verification = transferMapper.findGuardianVerificationForUpdate(
                userId.toString(), transferId.toString(), verificationId.toString());
        if (verification == null) throw new BusinessException(ErrorCode.GUARDIAN_VERIFICATION_NOT_FOUND);
        if (!"SENT".equals(verification.getStatus()) || !verification.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.GUARDIAN_CODE_EXPIRED);
        }
        String code = mockMmsSender.receivedCode(verificationId);
        if (code == null) throw new BusinessException(ErrorCode.GUARDIAN_VERIFICATION_NOT_FOUND);
        return code;
    }

    @Override
    @Transactional
    public void registerOrChangePin(UUID userId, TransferPinRequest request) {
        transferMapper.upsertPin(userId.toString(), PIN_ENCODER.encode(request.getPin()));
    }

    @Override
    @Transactional
    public TransferAuthenticationResponse authenticate(
            UUID userId, UUID transferId, String confirmationToken, TransferPinRequest request) {
        Transfer transfer = transferMapper.findOwnedByIdForUpdate(userId.toString(), transferId.toString());
        if (transfer == null) throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
        if (!"CONFIRMED".equals(transfer.getStatus())) throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        validateConfirmationToken(transfer, confirmationToken);
        UserTransferPin pin = transferMapper.findPinForUpdate(userId.toString());
        if (pin == null) throw new BusinessException(ErrorCode.TRANSFER_PIN_NOT_REGISTERED);
        OffsetDateTime now = OffsetDateTime.now(clock);
if (pin.getLockedUntil() != null) {
            if (pin.getLockedUntil().isAfter(now)) throw new BusinessException(ErrorCode.TRANSFER_PIN_LOCKED);
            transferMapper.resetPinFailures(userId.toString());
            pin.setFailedAttemptCount(0);
            pin.setLockedUntil(null);
        }
        if (!PIN_ENCODER.matches(request.getPin(), pin.getPinHash())) {
            OffsetDateTime lockedUntil = pin.getFailedAttemptCount() + 1 >= PIN_MAX_FAILURES
                    ? now.plus(PIN_LOCK_DURATION) : null;
            transferMapper.recordPinFailure(userId.toString(), lockedUntil);
            if (lockedUntil != null) throw new BusinessException(ErrorCode.TRANSFER_PIN_LOCKED);
            throw new BusinessException(ErrorCode.TRANSFER_PIN_INVALID);
        }
        transferMapper.resetPinFailures(userId.toString());
        transferMapper.expireAuthenticatedAuthentications(userId.toString(), transferId.toString());
        TransferAuthentication authentication = new TransferAuthentication();
        authentication.setTransferAuthenticationId(UUID.randomUUID().toString());
        authentication.setTransferId(transferId.toString()); authentication.setUserId(userId.toString());
        authentication.setStatus("AUTHENTICATED"); authentication.setAuthenticatedAt(now); authentication.setExpiresAt(now.plus(AUTHENTICATION_DURATION));
        transferMapper.insertAuthentication(authentication);
        return new TransferAuthenticationResponse(true, authentication.getExpiresAt());
    }

    @Override
    @Transactional
    public TransferResultResponse execute(
            UUID userId, UUID transferId, String confirmationToken, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        TransferTransaction existing = transferMapper.findTransactionByIdempotencyKey(userId.toString(), idempotencyKey);
        if (existing != null) {
            if (!transferId.toString().equals(existing.getTransferId())) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return result(existing);
        }
        Transfer transfer = transferMapper.findOwnedByIdForUpdate(userId.toString(), transferId.toString());
        if (transfer == null) throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
        // The transfer row serializes same-transfer executions. Re-read after obtaining its lock so a
        // request that waited for a prior execution returns that execution's original response.
        existing = transferMapper.findTransactionByIdempotencyKey(userId.toString(), idempotencyKey);
        if (existing != null) {
            if (!transferId.toString().equals(existing.getTransferId())) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return result(existing);
        }
        if (!"CONFIRMED".equals(transfer.getStatus())) throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        validateConfirmationToken(transfer, confirmationToken);
        TransferAuthentication authentication = transferMapper.findLatestAuthenticationForUpdate(userId.toString(), transferId.toString());
        if (authentication == null || !"AUTHENTICATED".equals(authentication.getStatus())) throw new BusinessException(ErrorCode.TRANSFER_AUTHENTICATION_REQUIRED);
        if (!authentication.getExpiresAt().isAfter(OffsetDateTime.now(clock))) throw new BusinessException(ErrorCode.TRANSFER_AUTHENTICATION_EXPIRED);
        TransferTransaction transaction = new TransferTransaction();
        transaction.setTransactionId(UUID.randomUUID().toString()); transaction.setTransferId(transfer.getTransferId());
        transaction.setUserId(userId.toString()); transaction.setFromAccountId(transfer.getFromAccountId());
        transaction.setRecipientId(transfer.getRecipientId());
        transaction.setAmount(transfer.getAmount()); transaction.setStatus("SUCCESS"); transaction.setIdempotencyKey(idempotencyKey);
        transaction.setTransferredAt(OffsetDateTime.now(clock));
        try {
            transferMapper.insertTransaction(transaction);
        } catch (DuplicateKeyException exception) {
            TransferTransaction concurrent = transferMapper.findTransactionByIdempotencyKey(userId.toString(), idempotencyKey);
            if (concurrent != null && transferId.toString().equals(concurrent.getTransferId())) return result(concurrent);
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (transferMapper.executeIfConfirmed(userId.toString(), transferId.toString()) != 1
                || transferMapper.consumeAuthentication(authentication.getTransferAuthenticationId()) != 1) {
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        return result(transaction);
    }

    private TransferResultResponse result(TransferTransaction transaction) {
        return new TransferResultResponse(UUID.fromString(transaction.getTransactionId()), UUID.fromString(transaction.getTransferId()), transaction.getStatus(), transaction.getAmount(), transaction.getTransferredAt());
    }
    private Transfer findOwned(UUID userId, UUID transferId) {
        Transfer transfer = transferMapper.findOwnedById(userId.toString(), transferId.toString());
        if (transfer == null) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
        }
        return transfer;
    }

    private void insertConfirmation(Transfer transfer, boolean approved) {
        TransferConfirmation confirmation = new TransferConfirmation();
        confirmation.setConfirmationId(UUID.randomUUID().toString());
        confirmation.setTransferId(transfer.getTransferId());
        confirmation.setUserId(transfer.getUserId());
        confirmation.setConfirmationType("FINAL_APPROVAL");
        confirmation.setApproved(approved);
        confirmation.setConfirmationText(confirmationText(
                transfer.getRecipientDisplayName(), transfer.getAmount()));
        confirmation.setConfirmedAt(OffsetDateTime.now(clock));
        transferMapper.insertConfirmation(confirmation);
    }

    private TransferConfirmResponse confirmResponse(
            Transfer transfer, boolean confirmed, String confirmationToken) {
        return new TransferConfirmResponse(
                UUID.fromString(transfer.getTransferId()), transfer.getStatus(), transfer.getCurrentStep(),
                confirmed, false, confirmationToken, transfer.getConfirmationTokenExpiresAt(),
                transfer.getApprovedAt());
    }

    private ConfirmationToken issueConfirmationToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new ConfirmationToken(
                value, sensitiveDataHasher.hash(value),
                OffsetDateTime.now(clock).plus(CONFIRMATION_TOKEN_DURATION));
    }

    private void validateConfirmationToken(Transfer transfer, String confirmationToken) {
        if (confirmationToken == null || confirmationToken.isBlank()
                || transfer.getConfirmationTokenHash() == null) {
            throw new BusinessException(ErrorCode.TRANSFER_CONFIRMATION_REQUIRED);
        }
        OffsetDateTime expiresAt = transfer.getConfirmationTokenExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.TRANSFER_CONFIRMATION_EXPIRED);
        }
        if (!MessageDigest.isEqual(
                transfer.getConfirmationTokenHash().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                sensitiveDataHasher.hash(confirmationToken).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.TRANSFER_CONFIRMATION_INVALID);
        }
    }

    private record ConfirmationToken(String value, String hash, OffsetDateTime expiresAt) { }

    private TransferResponse toResponse(Transfer transfer) {
        TransferRecipientResponse recipient = new TransferRecipientResponse(
                UUID.fromString(transfer.getRecipientId()), transfer.getRecipientDisplayName(),
                transfer.getRecipientBankCode(),
                masker.mask(crypto.decrypt(transfer.getRecipientAccountNumberEncrypted())));
        return new TransferResponse(
                UUID.fromString(transfer.getTransferId()), transfer.getStatus(), transfer.getCurrentStep(),
                UUID.fromString(transfer.getFromAccountId()), recipient, transfer.getAmount(),
                transfer.getRecognizedAmount(), parseCandidates(transfer.getAmountCandidates()),
                transfer.isAmountReconfirmRequired(),
                confirmationText(transfer.getRecipientDisplayName(), transfer.getAmount()),
                transfer.getPreparedAt(), transfer.getApprovedAt(), transfer.getExecutedAt());
    }

    private TransferRecipientResponse toRecipientResponse(Recipient recipient) {
        return new TransferRecipientResponse(
                UUID.fromString(recipient.getRecipientId()), recipient.getDisplayName(),
                recipient.getBankCode(), masker.mask(crypto.decrypt(recipient.getAccountNumberEncrypted())));
    }

    private List<Long> parseCandidates(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.TRANSFER_DATA_INVALID);
        }
    }

    private String confirmationText(String displayName, long amount) {
        return String.format("%s 님에게 %,d원을 보내시겠어요?", displayName, amount);
    }

    private void validatePositive(Long amount) {
        if (amount == null || amount <= 0) {
            throw invalidAmount();
        }
    }

    private BusinessException invalidAmount() {
        return new BusinessException(ErrorCode.INVALID_TRANSFER_AMOUNT);
    }

    private GuardianVerificationStateException guardianVerificationStateError(ErrorCode errorCode) {
        return new GuardianVerificationStateException(errorCode);
    }

    private String hashVerificationCode(String code) {
        return sensitiveDataHasher.hmacGuardianVerificationCode(code);
    }

    private MmsSendResult sendWithOneTemporaryRetry(
            UUID verificationId, String code, OffsetDateTime sentAt) {
        MmsSendResult first = mmsSender.send(verificationId, code, sentAt);
        if (first.delivered()
                || first.failureCode() != MmsSendResult.FailureCode.MMS_TEMPORARY_FAILURE) {
            return first;
        }
        return mmsSender.send(verificationId, code, sentAt);
    }
}
