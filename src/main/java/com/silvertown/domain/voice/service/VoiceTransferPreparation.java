package com.silvertown.domain.voice.service;

import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;

/** Verified amount, transfer draft, and the assessment produced for that draft. */
public record VoiceTransferPreparation(
        AmountValidationResponse amountValidation,
        TransferPrepareResponse transfer,
        RiskScoreResponse risk,
        TransferResponse readback) {}
