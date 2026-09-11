package com.silvertown.domain.voice.amount;

import com.silvertown.domain.voice.stt.AzureSpeechDetailedResult;
import com.silvertown.domain.voice.stt.AzureSpeechNBestAlternative;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic Korean won parser for Azure FINAL and N-best transcripts.
 *
 * <p>The parser deliberately accepts an amount only when the source sentence has exactly one
 * positive, whole-won expression ending in {@code 원}. It never asks an LLM to correct an amount.</p>
 */
@Component
public class KoreanAmountCandidateGenerator {
    private static final BigDecimal REASK_THRESHOLD = new BigDecimal("0.70");
    private static final BigDecimal RECONFIRM_THRESHOLD = new BigDecimal("0.90");
    private static final Pattern WON_AMOUNT = Pattern.compile(
            "(?<![-0-9.])([0-9일이삼사오육칠팔구영공십백천만억조서른마흔\\s,]+)\\s*원");

    public AmountCandidateDecision decide(AzureSpeechDetailedResult result) {
        LinkedHashSet<Long> candidates = new LinkedHashSet<>();
        boolean confusionPairApplied = false;
        for (String source : sources(result)) {
            OptionalLong amount = parseSingleAmount(source);
            if (amount.isEmpty()) {
                continue;
            }
            candidates.add(amount.getAsLong());
            OptionalLong confusionCandidate = confusionCandidate(source);
            if (confusionCandidate.isPresent() && confusionCandidate.getAsLong() != amount.getAsLong()) {
                candidates.add(confusionCandidate.getAsLong());
                confusionPairApplied = true;
            }
        }

        List<Long> resolvedCandidates = new ArrayList<>(candidates);
        if (result == null
                || result.confidence() == null
                || result.confidence().compareTo(REASK_THRESHOLD) < 0
                || resolvedCandidates.isEmpty()
                || resolvedCandidates.size() > 3) {
            return new AmountCandidateDecision(
                    AmountCandidateDecisionType.REASK, null, resolvedCandidates, confusionPairApplied);
        }
        if (result.confidence().compareTo(RECONFIRM_THRESHOLD) < 0
                || resolvedCandidates.size() > 1
                || confusionPairApplied) {
            return new AmountCandidateDecision(
                    AmountCandidateDecisionType.RECONFIRM, null, resolvedCandidates, confusionPairApplied);
        }
        Long recognizedAmount = resolvedCandidates.get(0);
        return new AmountCandidateDecision(
                AmountCandidateDecisionType.CONFIRMED,
                recognizedAmount,
                resolvedCandidates,
                false);
    }

    /** Text fallback also uses the same conservative Korean won parser as Azure FINAL results. */
    public AmountCandidateDecision decideText(String transcript, BigDecimal confidence) {
        return decide(new AzureSpeechDetailedResult(transcript, confidence, List.of()));
    }

    private List<String> sources(AzureSpeechDetailedResult result) {
        if (result == null) {
            return List.of();
        }
        List<String> sources = new ArrayList<>();
        if (!isBlank(result.transcript())) {
            sources.add(result.transcript());
        }
        for (AzureSpeechNBestAlternative alternative : result.nBestAlternatives()) {
            if (alternative != null && !isBlank(alternative.transcript())) {
                sources.add(alternative.transcript());
            }
        }
        return sources;
    }

    private OptionalLong confusionCandidate(String source) {
        if (source.contains("삼십")) {
            return parseSingleAmount(source.replaceFirst("삼십", "사십"));
        }
        if (source.contains("사십")) {
            return parseSingleAmount(source.replaceFirst("사십", "삼십"));
        }
        if (source.contains("서른")) {
            return parseSingleAmount(source.replaceFirst("서른", "마흔"));
        }
        if (source.contains("마흔")) {
            return parseSingleAmount(source.replaceFirst("마흔", "서른"));
        }
        return OptionalLong.empty();
    }

    private OptionalLong parseSingleAmount(String source) {
        if (isBlank(source)) {
            return OptionalLong.empty();
        }
        Matcher matcher = WON_AMOUNT.matcher(source);
        Long value = null;
        while (matcher.find()) {
            if (hasNegativePrefix(source, matcher.start())) {
                return OptionalLong.empty();
            }
            OptionalLong parsed = parseWonExpression(matcher.group(1));
            if (parsed.isEmpty()) {
                return OptionalLong.empty();
            }
            if (value != null) {
                return OptionalLong.empty();
            }
            value = parsed.getAsLong();
        }
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private boolean hasNegativePrefix(String source, int amountStart) {
        String prefix = source.substring(0, amountStart).trim();
        return prefix.endsWith("-") || prefix.endsWith("마이너스") || prefix.endsWith("음수");
    }

    private OptionalLong parseWonExpression(String expression) {
        String compact = expression.replaceAll("[\\s,]", "");
        if (compact.isEmpty() || compact.indexOf('조') >= 0) {
            return OptionalLong.empty();
        }
        try {
            long total = 0;
            long section = 0;
            long number = 0;
            long previousLargeUnit = Long.MAX_VALUE;
            for (int index = 0; index < compact.length(); ) {
                char character = compact.charAt(index);
                if (Character.isDigit(character)) {
                    int end = index + 1;
                    while (end < compact.length() && Character.isDigit(compact.charAt(end))) {
                        end++;
                    }
                    number = Long.parseLong(compact.substring(index, end));
                    index = end;
                    continue;
                }
                int digit = koreanDigit(character);
                if (digit >= 0) {
                    number = digit;
                    index++;
                    continue;
                }
                if (compact.startsWith("서른", index)) {
                    number = 30;
                    index += 2;
                    continue;
                }
                if (compact.startsWith("마흔", index)) {
                    number = 40;
                    index += 2;
                    continue;
                }
                long smallUnit = smallUnit(character);
                if (smallUnit > 0) {
                    section = Math.addExact(section, Math.multiplyExact(number == 0 ? 1 : number, smallUnit));
                    number = 0;
                    index++;
                    continue;
                }
                long largeUnit = largeUnit(character);
                if (largeUnit > 0) {
                    if (largeUnit >= previousLargeUnit) {
                        return OptionalLong.empty();
                    }
                    long group = Math.addExact(section, number);
                    total = Math.addExact(total, Math.multiplyExact(group == 0 ? 1 : group, largeUnit));
                    section = 0;
                    number = 0;
                    previousLargeUnit = largeUnit;
                    index++;
                    continue;
                }
                return OptionalLong.empty();
            }
            long value = Math.addExact(total, Math.addExact(section, number));
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException exception) {
            return OptionalLong.empty();
        }
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

    private long smallUnit(char character) {
        return switch (character) {
            case '십' -> 10;
            case '백' -> 100;
            case '천' -> 1_000;
            default -> 0;
        };
    }

    private long largeUnit(char character) {
        return switch (character) {
            case '만' -> 10_000;
            case '억' -> 100_000_000;
            default -> 0;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
