package com.silvertown.domain.risk.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.risk.blacklist.BlacklistAccountChecker;
import com.silvertown.domain.risk.blacklist.BlacklistResult;
import com.silvertown.domain.risk.dto.BlacklistCheckResponse;
import com.silvertown.domain.risk.dto.RiskCheckResponse;
import com.silvertown.domain.risk.dto.RiskFactorResponse;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.risk.mapper.RiskScoreMapper;
import com.silvertown.domain.risk.service.RiskPolicyCalculator;
import com.silvertown.domain.risk.service.RiskScoreService;
import com.silvertown.domain.risk.vo.RiskAssessment;
import com.silvertown.domain.risk.vo.RiskCheck;
import com.silvertown.domain.transfer.mapper.TransferMapper;
import com.silvertown.domain.transfer.vo.Transfer;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RiskScoreServiceImpl implements RiskScoreService {
    private final TransferMapper transferMapper;
    private final RiskScoreMapper riskScoreMapper;
    private final RiskPolicyCalculator calculator;
    private final BlacklistAccountChecker blacklistChecker;
    private final AccountNumberCrypto accountNumberCrypto;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public RiskScoreResponse assess(UUID userId, UUID transferId) {
        Evaluation result = evaluate(userId, transferId);
        holdIfRequired(userId, transferId, result);
        boolean hold = "HELD".equals(result.transfer.getStatus());
        boolean additionalCheckRequired = result.contextCheckRequired
                || result.score >= 30
                || "MATCH".equals(result.blacklist.getMatchStatus())
                || hold;
        saveAssessment(transferId, result, additionalCheckRequired);
        return new RiskScoreResponse(
                result.preScore, result.score, result.level, "RULE", result.contextCheckRequired,
                List.copyOf(result.factors),
                new BlacklistCheckResponse(
                        result.blacklist.getProvider(), result.blacklist.getMatchStatus()),
                hold ? "HOLD" : calculator.action(result.level),
                additionalCheckRequired);
    }

    @Override
    @Transactional
    public RiskCheckResponse checkContext(UUID userId, UUID transferId, String purposeAnswer) {
        Evaluation result = evaluate(userId, transferId);
        List<RiskFactorResponse> detectedConversationFactors =
                calculator.conversationFactors(purposeAnswer);
        RiskFactorResponse conversation = detectedConversationFactors.stream()
                .max(java.util.Comparator.comparingInt(RiskFactorResponse::getWeight))
                .orElse(null);
        if (conversation != null) {
            boolean selectedAdded = false;
            for (RiskFactorResponse factor : detectedConversationFactors) {
                if (!selectedAdded && factor.getCode().equals(conversation.getCode())) {
                    result.factors.add(factor);
                    selectedAdded = true;
                } else {
                    result.factors.add(new RiskFactorResponse(
                            factor.getCode(), factor.getLabel(), 0));
                }
            }
            result.score = clamp(result.preScore + conversation.getWeight());
            result.level = calculator.level(result.score);
        }
        boolean blacklistMatch = "MATCH".equals(result.blacklist.getMatchStatus());
        if (blacklistMatch) result.level = "CRITICAL";
        boolean verificationRequired = blacklistMatch || "CRITICAL".equals(result.level);
        holdIfRequired(userId, transferId, result);
        boolean hold = "HELD".equals(result.transfer.getStatus());
        boolean suspicious = blacklistMatch || conversation != null || hold;
        boolean additionalCheckRequired = suspicious || result.score >= 60;

        String warning = warning(result, conversation, blacklistMatch);
        saveAssessment(transferId, result, additionalCheckRequired);
        saveCheck(
                transferId, purposeAnswer, suspicious, hold, verificationRequired, warning);
        return new RiskCheckResponse(
                result.score, result.level, suspicious, hold,
                verificationRequired, additionalCheckRequired, warning, warning, null,
                List.copyOf(result.factors));
    }

    private Evaluation evaluate(UUID userId, UUID transferId) {
        Transfer transfer = transferMapper.findOwnedByIdForUpdate(
                userId.toString(), transferId.toString());
        if (transfer == null) throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
        ensureRiskAssessable(transfer);

        List<RiskFactorResponse> factors = new ArrayList<>();
        int recipientScore = recipientScore(userId, transfer, factors);
        int amountScore = amountScore(userId, transfer, factors);
        int behaviorScore = behaviorScore(userId, transfer, factors);
        int preScore = clamp(recipientScore + amountScore + behaviorScore);

        String accountNumber = accountNumberCrypto.decrypt(
                transfer.getRecipientAccountNumberEncrypted());
        BlacklistResult blacklist = blacklistChecker.check(
                transfer.getRecipientBankCode(), accountNumber);
        if ("MATCH".equals(blacklist.getMatchStatus())) {
            factors.add(new RiskFactorResponse(
                    "BLACKLIST_ACCOUNT_MATCH", "테스트 블랙리스트 계좌와 일치", 0));
        }

        boolean newRecipient = has(factors, "NEW_RECIPIENT");
        boolean contextRequired = preScore >= 30
                || (newRecipient && amountScore >= 10)
                || has(factors, "REPEATED_TRANSFER")
                || has(factors, "SPLIT_TRANSFER")
                || (newRecipient && has(factors, "UNUSUAL_NIGHT_TIME"));
        String level = "MATCH".equals(blacklist.getMatchStatus())
                ? "CRITICAL" : calculator.level(preScore);
        return new Evaluation(transfer, preScore, level, contextRequired, factors, blacklist);
    }

    private int recipientScore(UUID userId, Transfer transfer, List<RiskFactorResponse> factors) {
        OffsetDateTime last = riskScoreMapper.findLastSuccessfulTransferAt(
                userId.toString(), transfer.getRecipientId());
        if (last == null) {
            factors.add(new RiskFactorResponse("NEW_RECIPIENT", "처음 송금하는 수취인", 25));
            return 25;
        }
        if (last.isBefore(OffsetDateTime.now(clock).minusDays(365))) {
            factors.add(new RiskFactorResponse(
                    "DORMANT_RECIPIENT", "최근 1년간 송금하지 않은 수취인", 15));
            return 15;
        }
        return 0;
    }

    private int amountScore(UUID userId, Transfer transfer, List<RiskFactorResponse> factors) {
        int absolute = calculator.absoluteAmountScore(transfer.getAmount());
        if (absolute > 0) {
            factors.add(new RiskFactorResponse(
                    "ABSOLUTE_AMOUNT", "거래 자체의 규모가 큰 금액", absolute));
        }
        int userDeviation = calculator.deviationScore(
                riskScoreMapper.findRecentUserAmounts(userId.toString()), 10, transfer.getAmount());
        int recipientDeviation = calculator.deviationScore(
                riskScoreMapper.findRecentRecipientAmounts(
                        userId.toString(), transfer.getRecipientId()),
                5,
                transfer.getAmount());
        int deviation = Math.max(userDeviation, recipientDeviation);
        int appliedDeviation = Math.min(deviation, 30 - absolute);
        if (appliedDeviation > 0) {
            String label = recipientDeviation >= userDeviation
                    ? "해당 수취인에게 평소 보내던 금액보다 큼"
                    : "사용자의 평소 송금 금액보다 큼";
            factors.add(new RiskFactorResponse(
                    "USER_BEHAVIOR_AMOUNT_DEVIATION", label, appliedDeviation));
        }
        return absolute + appliedDeviation;
    }

    private int behaviorScore(UUID userId, Transfer transfer, List<RiskFactorResponse> factors) {
        int score = 0;
        int attempts = riskScoreMapper.countRecentAttempts(
                userId.toString(), transfer.getRecipientId());
        int distinct = riskScoreMapper.countRecentDistinctRecipients(userId.toString());
        if (distinct >= 3) {
            factors.add(new RiskFactorResponse(
                    "SPLIT_TRANSFER", "30분 내 여러 계좌로 분산 송금 시도", 30));
            score += 30;
        } else if (attempts >= 2) {
            factors.add(new RiskFactorResponse(
                    "REPEATED_TRANSFER", "30분 내 동일 수취인에게 반복 송금 시도", 20));
            score += 20;
        }
        if (transfer.getPreparedAt() != null && transfer.getPreparedAt().getHour() < 6) {
            factors.add(new RiskFactorResponse(
                    "UNUSUAL_NIGHT_TIME", "심야 시간대 송금 시도", 10));
            score += 10;
        }
        return score;
    }

    private void holdIfRequired(UUID userId, UUID transferId, Evaluation result) {
        boolean holdRequired = result.score >= 60
                || "MATCH".equals(result.blacklist.getMatchStatus());
        if (!holdRequired || "HELD".equals(result.transfer.getStatus())) return;
        ensureRiskAssessable(result.transfer);
        if (riskScoreMapper.holdTransfer(userId.toString(), transferId.toString()) != 1) {
            Transfer current = transferMapper.findOwnedById(
                    userId.toString(), transferId.toString());
            if (current == null) throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        result.transfer.setStatus("HELD");
    }

    private void saveAssessment(
            UUID transferId, Evaluation result, boolean additionalCheckRequired) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId(UUID.randomUUID().toString());
        assessment.setTransferId(transferId.toString());
        assessment.setSource("RULE");
        assessment.setScore(result.score);
        assessment.setLevel(result.level);
        assessment.setFactors(toJson(result.factors));
        assessment.setAdditionalCheckRequired(additionalCheckRequired);
        assessment.setAssessedAt(OffsetDateTime.now(clock));
        riskScoreMapper.insertAssessment(assessment);
    }

    private void saveCheck(
            UUID transferId,
            String purposeAnswer,
            boolean suspicious,
            boolean hold,
            boolean verificationRequired,
            String warning) {
        RiskCheck check = new RiskCheck();
        check.setRiskCheckId(UUID.randomUUID().toString());
        check.setTransferId(transferId.toString());
        check.setPurposeAnswer(purposeAnswer);
        check.setSuspicious(suspicious);
        check.setHold(hold);
        check.setAdditionalAuthRequired(verificationRequired);
        check.setMessage(warning);
        check.setCheckedAt(OffsetDateTime.now(clock));
        riskScoreMapper.insertRiskCheck(check);
    }

    private String warning(
            Evaluation result, RiskFactorResponse conversation, boolean blacklistMatch) {
        if (blacklistMatch) {
            return "금융사고 신고 이력이 확인되어 송금을 잠시 보류했어요. 은행 고객센터에 문의해 주세요.";
        }
        if (conversation != null) return conversation.getLabel() + "이 확인되어 한 번 더 확인할게요.";
        if (result.score >= 60) return "평소와 다른 거래 요소가 있어 송금을 잠시 보류했어요.";
        return "확인이 완료되었습니다. 수취인과 금액을 다시 확인해 주세요.";
    }

    private String toJson(List<RiskFactorResponse> factors) {
        try {
            return objectMapper.writeValueAsString(factors);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.TRANSFER_DATA_INVALID);
        }
    }

    private boolean has(List<RiskFactorResponse> factors, String code) {
        return factors.stream().anyMatch(factor -> code.equals(factor.getCode()));
    }

    private void ensureRiskAssessable(Transfer transfer) {
        String status = transfer.getStatus();
        if (!"DRAFT".equals(status)
                && !"RECONFIRM".equals(status)
                && !"CONFIRMED".equals(status)
                && !"HELD".equals(status)) {
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
    }

    private int clamp(int score) {
        return Math.min(100, Math.max(0, score));
    }

    private static final class Evaluation {
        private final Transfer transfer;
        private final int preScore;
        private int score;
        private String level;
        private final boolean contextCheckRequired;
        private final List<RiskFactorResponse> factors;
        private final BlacklistResult blacklist;

        private Evaluation(
                Transfer transfer,
                int preScore,
                String level,
                boolean contextCheckRequired,
                List<RiskFactorResponse> factors,
                BlacklistResult blacklist) {
            this.transfer = transfer;
            this.preScore = preScore;
            this.score = preScore;
            this.level = level;
            this.contextCheckRequired = contextCheckRequired;
            this.factors = factors;
            this.blacklist = blacklist;
        }
    }
}
