package com.silvertown.domain.bill.client;

public interface BillOcrClient {
    BillOcrCandidate analyze(byte[] imageBytes, String contentType);
}
