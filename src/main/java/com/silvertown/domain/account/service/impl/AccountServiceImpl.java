package com.silvertown.domain.account.service.impl;
import com.silvertown.domain.account.dto.AccountResponse;
import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.account.service.AccountService;
import com.silvertown.domain.account.vo.BankAccount;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import com.silvertown.global.security.crypto.AccountNumberMasker;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountMapper accountMapper;
    private final AccountNumberCrypto crypto;
    private final AccountNumberMasker masker;
    public List<AccountResponse> getAccounts(UUID userId) {
        return accountMapper.findActiveByUserId(userId.toString()).stream().map(this::toResponse).collect(Collectors.toList());
    }
    private AccountResponse toResponse(BankAccount a) {
        return new AccountResponse(UUID.fromString(a.getAccountId()), a.getBankCode(), masker.mask(crypto.decrypt(a.getAccountNumberEncrypted())), a.getAccountName(), a.getBalance(), a.getAccountType(), a.isActive(), a.getSyncedAt());
    }
}
