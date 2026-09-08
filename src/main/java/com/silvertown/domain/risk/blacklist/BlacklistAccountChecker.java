package com.silvertown.domain.risk.blacklist;

public interface BlacklistAccountChecker {
    BlacklistResult check(String bankCode, String accountNumber);
}
