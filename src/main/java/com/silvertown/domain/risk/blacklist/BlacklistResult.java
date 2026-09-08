package com.silvertown.domain.risk.blacklist;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BlacklistResult {
    private final String provider;
    private final String matchStatus;
}
