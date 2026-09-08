package com.silvertown.domain.voice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.bill.service.BillService;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.risk.service.RiskScoreService;
import com.silvertown.domain.recipient.service.RecipientService;
import com.silvertown.domain.voice.amount.KoreanAmountCandidateGenerator;
import com.silvertown.domain.voice.dto.VoiceTurnRequest;
import com.silvertown.domain.voice.dto.VoiceTurnResponse;
import com.silvertown.domain.voice.dto.VoiceUiActionRequest;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.mapper.VoiceUiActionMapper;
import com.silvertown.domain.voice.service.AccountVoiceResponseResolver;
import com.silvertown.domain.voice.service.BillVoiceResponseResolver;
import com.silvertown.domain.voice.service.MobileBranchVoiceResponseResolver;
import com.silvertown.domain.voice.service.VoiceProgressPromptFactory;
import com.silvertown.domain.voice.service.VoiceInteractionCardIssuer;
import com.silvertown.domain.voice.service.VoiceSsmlRenderer;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisPort;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisResult;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class VoiceTurnServiceTransactionIntegrationTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String FIRST_TURN_ID = "20000000-0000-0000-0000-000000000001";
    private static final String SECOND_TURN_ID = "20000000-0000-0000-0000-000000000002";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T01:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CountDownLatch analysisStarted = new CountDownLatch(1);
    private final CountDownLatch releaseAnalysis = new CountDownLatch(1);
    private PooledDataSource dataSource;
    private VoiceSessionMapper voiceSessionMapper;
    private VoiceTurnServiceImpl service;
    private VoiceUiActionServiceImpl voiceUiActionService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        SqlSessionFactory sessionFactory = newSessionFactory();
        createSchema();

        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sessionFactory);
        voiceSessionMapper = sqlSessionTemplate.getMapper(VoiceSessionMapper.class);
        DialogueTurnMapper dialogueTurnMapper = sqlSessionTemplate.getMapper(DialogueTurnMapper.class);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        VoiceTurnAnalysisPort blockingAnalysisPort = command -> {
            analysisStarted.countDown();
            try {
                if (!releaseAnalysis.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Test did not release the blocked analysis.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Blocked analysis was interrupted.", exception);
            }
            return analysis();
        };
        service = new VoiceTurnServiceImpl(
                voiceSessionMapper,
                dialogueTurnMapper,
                blockingAnalysisPort,
                new VoiceProgressPromptFactory(objectMapper),
                new VoiceSsmlRenderer(org.mockito.Mockito.mock(UserVoiceSettingsMapper.class)),
                org.mockito.Mockito.mock(KoreanAmountCandidateGenerator.class),
                testOrchestrator(
                        org.mockito.Mockito.mock(TransferService.class),
                        org.mockito.Mockito.mock(RiskScoreService.class),
                        org.mockito.Mockito.mock(RecipientService.class)),
                org.mockito.Mockito.mock(VoiceInteractionCardIssuer.class),
                org.mockito.Mockito.mock(VoiceInteractionCardMapper.class),
                passthroughAccountResolver(),
                new BillVoiceResponseResolver(org.mockito.Mockito.mock(BillService.class), objectMapper, CLOCK),
                passthroughMobileBranchResolver(),
                objectMapper,
                CLOCK,
                transactionManager);
        voiceUiActionService = new VoiceUiActionServiceImpl(
                voiceSessionMapper,
                org.mockito.Mockito.mock(VoiceInteractionCardMapper.class),
                org.mockito.Mockito.mock(VoiceUiActionMapper.class),
                org.mockito.Mockito.mock(DialogueTurnMapper.class),
                testUiActionOrchestrator(
                        org.mockito.Mockito.mock(TransferService.class),
                        org.mockito.Mockito.mock(RiskScoreService.class)),
                org.mockito.Mockito.mock(VoiceSsmlRenderer.class),
                objectMapper,
                CLOCK);

        new TransactionTemplate(transactionManager).execute(status -> {
            voiceSessionMapper.insert(newSession());
            return null;
        });
    }

    private VoiceTransferOrchestratorImpl testOrchestrator(
            TransferService transferService,
            RiskScoreService riskScoreService,
            RecipientService recipientService) {
        return new VoiceTransferOrchestratorImpl(
                new RecipientServiceRecipientCandidateAdapter(recipientService),
                new TransferServiceAmountValidationAdapter(transferService),
                new TransferServicePrepareAdapter(transferService),
                new TransferServiceRiskAssessmentAdapter(riskScoreService),
                new TransferServiceReadAdapter(transferService),
                new TransferServiceCancellationAdapter(transferService),
                new TransferServiceConfirmAdapter(transferService),
                new VoiceSessionMapperTransferLinkAdapter(voiceSessionMapper));
    }

    private VoiceTransferOrchestratorImpl testUiActionOrchestrator(
            TransferService transferService, RiskScoreService riskScoreService) {
        return new VoiceTransferOrchestratorImpl(
                (userId, keyword) -> java.util.List.of(),
                new TransferServiceAmountValidationAdapter(transferService),
                new TransferServicePrepareAdapter(transferService),
                new TransferServiceRiskAssessmentAdapter(riskScoreService),
                new TransferServiceReadAdapter(transferService),
                new TransferServiceCancellationAdapter(transferService),
                new TransferServiceConfirmAdapter(transferService),
                new VoiceSessionMapperTransferLinkAdapter(voiceSessionMapper));
    }

    private AccountVoiceResponseResolver passthroughAccountResolver() {
        AccountVoiceResponseResolver resolver = org.mockito.Mockito.mock(AccountVoiceResponseResolver.class);
        org.mockito.Mockito.when(resolver.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return resolver;
    }

    private MobileBranchVoiceResponseResolver passthroughMobileBranchResolver() {
        MobileBranchVoiceResponseResolver resolver = org.mockito.Mockito.mock(MobileBranchVoiceResponseResolver.class);
        org.mockito.Mockito.when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return resolver;
    }

    @AfterEach
    void releaseResources() {
        releaseAnalysis.countDown();
        if (dataSource != null) {
            dataSource.forceCloseAll();
        }
    }

    @Test
    void commitsTheProcessingClaimBeforeBlockedAnalysisAndRejectsAnotherTurn() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<VoiceTurnResponse> firstRequest = executor.submit(
                    () -> service.process(USER_ID, SESSION_ID, request(FIRST_TURN_ID)));
            assertTrue(analysisStarted.await(1, TimeUnit.SECONDS));
            assertEquals(VoiceSessionStatus.PROCESSING.name(), currentSessionStatus());

            BusinessException conflict = assertThrows(
                    BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request(SECOND_TURN_ID)));
            assertEquals("VOICE_TURN_CONFLICT", conflict.getErrorCode().getCode());

            releaseAnalysis.countDown();
            assertEquals(DialogueStep.AWAITING_AMOUNT,
                    firstRequest.get(1, TimeUnit.SECONDS).getState());
            assertEquals(VoiceSessionStatus.SPEAKING.name(), currentSessionStatus());
        } finally {
            releaseAnalysis.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsUiActionWhileConcurrentTurnOwnsTheSession() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<VoiceTurnResponse> turn = executor.submit(
                    () -> service.process(USER_ID, SESSION_ID, request(FIRST_TURN_ID)));
            assertTrue(analysisStarted.await(1, TimeUnit.SECONDS));

            BusinessException conflict = assertThrows(BusinessException.class,
                    () -> voiceUiActionService.process(USER_ID, SESSION_ID, uiActionRequest()));

            assertEquals("VOICE_TURN_CONFLICT", conflict.getErrorCode().getCode());
            releaseAnalysis.countDown();
            turn.get(1, TimeUnit.SECONDS);
        } finally {
            releaseAnalysis.countDown();
            executor.shutdownNow();
        }
    }

    private VoiceUiActionRequest uiActionRequest() throws Exception {
        return objectMapper.readValue("""
                {"actionId":"40000000-0000-0000-0000-000000000001",
                 "sourceTurnId":"20000000-0000-0000-0000-000000000001",
                 "cardId":"30000000-0000-0000-0000-000000000001",
                 "cardVersion":1,"actionType":"SELECT_RECIPIENT",
                 "itemId":"50000000-0000-0000-0000-000000000001"}
                """, VoiceUiActionRequest.class);
    }

    private SqlSessionFactory newSessionFactory() throws Exception {
        Configuration configuration = new Configuration(
                new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(VoiceSessionMapper.class);
        configuration.addMapper(DialogueTurnMapper.class);
        parseMapper(configuration, "mapper/voice/VoiceSessionMapper.xml");
        parseMapper(configuration, "mapper/voice/DialogueTurnMapper.xml");
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void parseMapper(Configuration configuration, String resourcePath) throws Exception {
        try (var mapperXml = Resources.getResourceAsReader(resourcePath)) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    mapperXml, configuration, resourcePath, configuration.getSqlFragments()).parse();
        }
    }

    private void createSchema() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE voice_sessions ("
                    + "session_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, "
                    + "from_account_id CHAR(36) NULL, transfer_id CHAR(36) NULL, "
                    + "status VARCHAR(20) NOT NULL, current_step VARCHAR(30) NOT NULL, "
                    + "flow_type VARCHAR(30) NOT NULL, stt_mode VARCHAR(20) NOT NULL, "
                    + "entry_point VARCHAR(30) NOT NULL, "
                    + "started_at TIMESTAMP NOT NULL, ended_at TIMESTAMP NULL, expires_at TIMESTAMP NOT NULL)");
            statement.execute("CREATE TABLE dialogue_turns ("
                    + "turn_id CHAR(36) PRIMARY KEY, session_id CHAR(36) NOT NULL, sequence_no INT NOT NULL, "
                    + "speaker VARCHAR(10) NOT NULL, transcript CLOB NULL, tts_text CLOB NULL, "
                    + "tts_ssml CLOB NULL, display_card CLOB NULL, step VARCHAR(30) NULL, intent VARCHAR(50) NULL, "
                    + "extracted_slots CLOB NULL, silence_ms INT NOT NULL DEFAULT 0, "
                    + "replay_count INT NOT NULL DEFAULT 0, interrupted BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "stt_confidence DECIMAL(5,4) NULL, input_type VARCHAR(20) NULL, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "CONSTRAINT uk_dialogue_turns_session_sequence UNIQUE (session_id, sequence_no))");
        }
    }

    private VoiceSessionVo newSession() {
        VoiceSessionVo voiceSession = new VoiceSessionVo();
        voiceSession.setSessionId(SESSION_ID);
        voiceSession.setUserId(USER_ID);
        voiceSession.setStatus(VoiceSessionStatus.LISTENING.name());
        voiceSession.setCurrentStep(DialogueStep.AWAITING_INPUT.name());
        voiceSession.setFlowType(VoiceFlowType.GENERAL_FINANCE.name());
        voiceSession.setSttMode("CLIENT");
        voiceSession.setEntryPoint("GENERAL_FINANCE");
        voiceSession.setStartedAt(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        voiceSession.setExpiresAt(LocalDateTime.ofInstant(CLOCK.instant().plusSeconds(60), ZoneOffset.UTC));
        return voiceSession;
    }

    private VoiceTurnRequest request(String turnId) throws Exception {
        return objectMapper.readValue(
                "{\"turnId\":\"" + turnId + "\",\"transcript\":\"김철수에게 오만원 보내줘\","
                        + "\"sttConfidence\":0.95,\"inputType\":\"VOICE\"}",
                VoiceTurnRequest.class);
    }

    private VoiceTurnAnalysisResult analysis() {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_AMOUNT,
                VoiceIntent.TRANSFER,
                Map.of("recipient", "김철수"),
                new BigDecimal("0.95"),
                "김철수 님에게 보낼 금액을 말씀해 주세요.",
                "<speak>김철수 님에게 보낼 금액을 말씀해 주세요.</speak>",
                objectMapper.valueToTree(Map.of("recipient", "김철수")),
                objectMapper.valueToTree(Map.of("name", "amount")),
                objectMapper.valueToTree(Map.of("recipient", "김철수")),
                VoiceNextAction.ASK_AMOUNT,
                VoiceRequestedFunction.TRANSFER_RECIPIENT_CANDIDATES);
    }

    private String currentSessionStatus() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT status FROM voice_sessions WHERE session_id = ?")) {
            statement.setString(1, SESSION_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString("status");
            }
        }
    }
}
