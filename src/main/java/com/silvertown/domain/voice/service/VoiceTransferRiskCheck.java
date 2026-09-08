package com.silvertown.domain.voice.service;

import com.silvertown.domain.risk.dto.RiskCheckResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;

/** Context-risk outcome paired with the authoritative transfer read-back data. */
public record VoiceTransferRiskCheck(RiskCheckResponse risk, TransferResponse transfer) {}
