package com.silvertown.domain.voice.stt;

import java.math.BigDecimal;

/** A provider-ranked alternative from an Azure Speech detailed final result. */
public record AzureSpeechNBestAlternative(String transcript, BigDecimal confidence) {}
