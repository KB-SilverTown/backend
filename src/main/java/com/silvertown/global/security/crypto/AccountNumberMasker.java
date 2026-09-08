package com.silvertown.global.security.crypto;

import org.springframework.stereotype.Component;

@Component
public class AccountNumberMasker {
    public String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "****";
        }
        String digits = accountNumber.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "***-***-" + digits.substring(digits.length() - 4);
    }
}
