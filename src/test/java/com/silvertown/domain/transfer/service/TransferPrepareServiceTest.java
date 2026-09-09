package com.silvertown.domain.transfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.account.vo.BankAccount;
import com.silvertown.domain.recipient.mapper.RecipientMapper;
import com.silvertown.domain.recipient.vo.Recipient;
import com.silvertown.domain.transfer.dto.TransferPrepareRequest;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.mapper.TransferMapper;
import com.silvertown.domain.transfer.service.impl.TransferServiceImpl;
import com.silvertown.domain.transfer.vo.Transfer;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import com.silvertown.global.security.crypto.AccountNumberMasker;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class TransferPrepareServiceTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RECIPIENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TRANSFER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private final AccountMapper accountMapper = Mockito.mock(AccountMapper.class);
    private final RecipientMapper recipientMapper = Mockito.mock(RecipientMapper.class);
    private final TransferMapper transferMapper = Mockito.mock(TransferMapper.class);
    private final AccountNumberCrypto crypto = Mockito.mock(AccountNumberCrypto.class);
    private final AccountNumberMasker masker = Mockito.mock(AccountNumberMasker.class);
    private final TransferService service = new TransferServiceImpl(
            accountMapper, recipientMapper, transferMapper, crypto, masker, new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-09-06T01:00:00Z"), ZoneOffset.UTC), null, null);

    @Test
    void reusesTheSameDraftForTheSameVoiceSessionPreparation() {
        BankAccount account = new BankAccount();
        account.setBalance(100_000L);
        Recipient recipient = new Recipient();
        recipient.setRecipientId(RECIPIENT_ID.toString());
        recipient.setDisplayName("김철수");
        recipient.setBankCode("004");
        recipient.setAccountNumberEncrypted(new byte[] {1, 2, 3});
        Transfer draft = new Transfer();
        draft.setTransferId(TRANSFER_ID.toString());
        draft.setFromAccountId(ACCOUNT_ID.toString());
        draft.setRecipientId(RECIPIENT_ID.toString());
        draft.setAmount(50_000L);
        draft.setStatus("DRAFT");
        draft.setCurrentStep("RISK_CHECK");
        draft.setPreparedAt(OffsetDateTime.parse("2026-09-06T01:00:00Z"));

        when(accountMapper.findOwnedActiveById(anyString(), anyString())).thenReturn(account);
        when(recipientMapper.findOwnedById(anyString(), anyString())).thenReturn(recipient);
        when(transferMapper.findOwnedVoiceSessionIdForUpdate(USER_ID.toString(), SESSION_ID.toString()))
                .thenReturn(SESSION_ID.toString());
        when(transferMapper.findOwnedDraftByVoiceSession(USER_ID.toString(), SESSION_ID.toString()))
                .thenReturn(draft);
        when(crypto.decrypt(recipient.getAccountNumberEncrypted())).thenReturn("1234567890");
        when(masker.mask("1234567890")).thenReturn("***-***-7890");

        TransferPrepareResponse response = service.prepare(
                USER_ID, TransferPrepareRequest.of(ACCOUNT_ID, RECIPIENT_ID, 50_000L, SESSION_ID));

        assertEquals(TRANSFER_ID, response.getTransferId());
        assertEquals("DRAFT", response.getStatus());
        verify(transferMapper, never()).insert(Mockito.any());
    }

    @Test
    void preservesTheAmountAlreadyExtractedWithTheRecipientInTheVoiceTurn() {
        BankAccount account = new BankAccount();
        account.setBalance(500_000L);
        Recipient recipient = new Recipient();
        recipient.setRecipientId(RECIPIENT_ID.toString());
        recipient.setDisplayName("김철수");
        recipient.setBankCode("004");
        recipient.setAccountNumberEncrypted(new byte[] {1, 2, 3});
        when(accountMapper.findOwnedActiveById(anyString(), anyString())).thenReturn(account);
        when(recipientMapper.findOwnedById(anyString(), anyString())).thenReturn(recipient);
        when(transferMapper.findOwnedVoiceSessionIdForUpdate(USER_ID.toString(), SESSION_ID.toString()))
                .thenReturn(SESSION_ID.toString());
        when(transferMapper.findOwnedDraftByVoiceSession(USER_ID.toString(), SESSION_ID.toString()))
                .thenReturn(null);
        when(crypto.decrypt(recipient.getAccountNumberEncrypted())).thenReturn("1234567890");
        when(masker.mask("1234567890")).thenReturn("***-***-7890");

        TransferPrepareResponse response = service.prepare(
                USER_ID, TransferPrepareRequest.of(ACCOUNT_ID, RECIPIENT_ID, 300_000L, SESSION_ID));

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferMapper).insert(transferCaptor.capture());
        assertEquals(300_000L, response.getAmount());
        assertEquals(300_000L, transferCaptor.getValue().getAmount());
        assertEquals("김철수 님에게 300,000원을 보내시겠어요?", response.getConfirmationText());
    }
}
