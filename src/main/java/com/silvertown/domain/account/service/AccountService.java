package com.silvertown.domain.account.service;
import com.silvertown.domain.account.dto.AccountResponse;
import java.util.List;
import java.util.UUID;
public interface AccountService { List<AccountResponse> getAccounts(UUID userId); }
