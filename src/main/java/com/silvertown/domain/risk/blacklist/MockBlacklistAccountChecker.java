package com.silvertown.domain.risk.blacklist;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockBlacklistAccountChecker implements BlacklistAccountChecker {
    private final Set<String> accountHashes;

    public MockBlacklistAccountChecker(
            @Value("${risk.blacklist.mock.account-hashes:}") String configuredHashes) {
        if (configuredHashes == null || configuredHashes.isBlank()) {
            this.accountHashes = Collections.emptySet();
            return;
        }
        this.accountHashes = Arrays.stream(configuredHashes.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public BlacklistResult check(String bankCode, String accountNumber) {
        if (bankCode == null || bankCode.isBlank() || accountNumber == null || accountNumber.isBlank()) {
            return new BlacklistResult("MOCK", "UNKNOWN");
        }
        String accountHash = sha256(bankCode + ":" + accountNumber);
        return new BlacklistResult(
                "MOCK", accountHashes.contains(accountHash) ? "MATCH" : "NO_MATCH");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
