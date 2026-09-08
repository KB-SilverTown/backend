package com.silvertown.domain.voice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.account.dto.AccountResponse;
import com.silvertown.domain.account.service.AccountService;
import com.silvertown.domain.bill.service.BillService;
import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.recipient.service.RecipientService;
import com.silvertown.domain.risk.service.RiskScoreService;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.voice.amount.KoreanAmountCandidateGenerator;
import com.silvertown.domain.voice.dto.SpeechTokenResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.AccountVoiceResponseResolver;
import com.silvertown.domain.voice.service.BillVoiceResponseResolver;
import com.silvertown.domain.voice.service.MobileBranchVoiceResponseResolver;
import com.silvertown.domain.voice.service.SpeechTokenService;
import com.silvertown.domain.voice.service.VoiceProgressPromptFactory;
import com.silvertown.domain.voice.service.VoiceInteractionCardIssuer;
import com.silvertown.domain.voice.service.VoiceSessionPromptProvider;
import com.silvertown.domain.voice.service.VoiceSsmlRenderer;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisPort;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisResult;
import com.silvertown.domain.voice.service.impl.VoiceSessionServiceImpl;
import com.silvertown.domain.voice.service.impl.VoiceSettingsServiceImpl;
import com.silvertown.domain.voice.service.impl.VoiceTurnServiceImpl;
import com.silvertown.domain.voice.service.impl.VoiceTransferOrchestratorImpl;
import com.silvertown.domain.voice.service.impl.RecipientServiceRecipientCandidateAdapter;
import com.silvertown.domain.voice.service.impl.TransferServiceAmountValidationAdapter;
import com.silvertown.domain.voice.service.impl.TransferServiceCancellationAdapter;
import com.silvertown.domain.voice.service.impl.TransferServiceConfirmAdapter;
import com.silvertown.domain.voice.service.impl.TransferServicePrepareAdapter;
import com.silvertown.domain.voice.service.impl.TransferServiceReadAdapter;
import com.silvertown.domain.voice.service.impl.TransferServiceRiskAssessmentAdapter;
import com.silvertown.domain.voice.service.impl.VoiceSessionMapperTransferLinkAdapter;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
import com.silvertown.global.security.JwtAuthenticationFilter;
import com.silvertown.global.security.JwtTokenProvider;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VoiceApiIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-02T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private PooledDataSource dataSource;
    private VoiceSessionMapper voiceSessionMapper;
    private DialogueTurnMapper dialogueTurnMapper;
    private JwtTokenProvider jwtTokenProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        SqlSessionFactory sessionFactory = newSessionFactory();
        createSchema(sessionFactory);

        UserVoiceSettingsMapper userVoiceSettingsMapper = autocommitMapper(
                sessionFactory, UserVoiceSettingsMapper.class);
        voiceSessionMapper = autocommitMapper(sessionFactory, VoiceSessionMapper.class);
        dialogueTurnMapper = autocommitMapper(sessionFactory, DialogueTurnMapper.class);
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sessionFactory);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        VoiceTurnAnalysisPort voiceTurnAnalysisPort = command -> accountInquiryAnalysis();
        AccountService accountService = org.mockito.Mockito.mock(AccountService.class);
        org.mockito.Mockito.when(accountService.getAccounts(USER_ID)).thenReturn(java.util.List.of(accountResponse()));
        VoiceTurnServiceImpl voiceTurnService = new VoiceTurnServiceImpl(
                sqlSessionTemplate.getMapper(VoiceSessionMapper.class),
                sqlSessionTemplate.getMapper(DialogueTurnMapper.class),
                voiceTurnAnalysisPort,
                new VoiceProgressPromptFactory(objectMapper),
                new VoiceSsmlRenderer(userVoiceSettingsMapper),
                org.mockito.Mockito.mock(KoreanAmountCandidateGenerator.class),
                testOrchestrator(),
                new VoiceInteractionCardIssuer(
                        org.mockito.Mockito.mock(VoiceInteractionCardMapper.class), objectMapper),
                org.mockito.Mockito.mock(VoiceInteractionCardMapper.class),
                new AccountVoiceResponseResolver(accountService, objectMapper),
                new BillVoiceResponseResolver(org.mockito.Mockito.mock(BillService.class), objectMapper, CLOCK),
                new MobileBranchVoiceResponseResolver(objectMapper),
                objectMapper,
                CLOCK,
                transactionManager);
        AuthenticatedUserId authenticatedUserId = new AuthenticatedUserId();
        SpeechTokenService speechTokenService = () -> new SpeechTokenResponse(
                "test-speech-token", "koreacentral",
                OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC).plusMinutes(9));

        jwtTokenProvider = new JwtTokenProvider(
                CLOCK,
                Base64.getEncoder().encodeToString(new byte[32]),
                3600,
                1209600);
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);
        SecurityContextHolderAwareRequestFilter requestFilter =
                new SecurityContextHolderAwareRequestFilter();
        requestFilter.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VoiceSettingsController(
                                new VoiceSettingsServiceImpl(userVoiceSettingsMapper, CLOCK),
                                authenticatedUserId),
                        new SpeechTokenController(speechTokenService, authenticatedUserId),
                        new VoiceSessionController(
                                new VoiceSessionServiceImpl(
                                        voiceSessionMapper,
                                        org.mockito.Mockito.mock(AccountMapper.class),
                                        testOrchestrator(),
                                        org.mockito.Mockito.mock(VoiceInteractionCardMapper.class),
                                        dialogueTurnMapper,
                                        new VoiceSessionPromptProvider(),
                                        new VoiceSsmlRenderer(userVoiceSettingsMapper),
                                        objectMapper,
                                        CLOCK),
                                authenticatedUserId),
                        new VoiceTurnController(voiceTurnService, authenticatedUserId))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), jwtAuthenticationFilter, requestFilter)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private VoiceTransferOrchestratorImpl testOrchestrator() {
        TransferService transferService = org.mockito.Mockito.mock(TransferService.class);
        RiskScoreService riskScoreService = org.mockito.Mockito.mock(RiskScoreService.class);
        RecipientService recipientService = org.mockito.Mockito.mock(RecipientService.class);
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

    @AfterEach
    void releaseTestResources() {
        SecurityContextHolder.clearContext();
        if (dataSource != null) {
            dataSource.forceCloseAll();
        }
    }

    @Test
    void authenticatedUserCompletesVoiceSettingsTokenAndSessionFlow() throws Exception {
        JsonNode settings = response(mockMvc.perform(put("/api/users/me/voice-settings")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ttsVoice\":\"ko-KR-GookMinNeural\",\"speechRateMultiplier\":1.10," +
                                "\"volumeMultiplier\":1.15}"))
                .andExpect(status().isOk()));
        assertEquals("ko-KR-GookMinNeural", settings.get("ttsVoice").asText());
        assertEquals(1.10, settings.get("speechRateMultiplier").asDouble());
        assertEquals(1.15, settings.get("volumeMultiplier").asDouble());

        JsonNode speechToken = response(mockMvc.perform(post("/api/voice/speech-token")
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("test-speech-token", speechToken.get("token").asText());
        assertEquals("koreacentral", speechToken.get("region").asText());

        JsonNode createdSession = response(mockMvc.perform(post("/api/voice/sessions")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryPoint\":\"GENERAL_FINANCE\"}"))
                .andExpect(status().isCreated()));
        String sessionId = createdSession.get("sessionId").asText();
        assertEquals("LISTENING", createdSession.get("status").asText());
        assertEquals("GENERAL_FINANCE", createdSession.get("entryPoint").asText());

        JsonNode foundSession = response(mockMvc.perform(get("/api/voice/sessions/{sessionId}", sessionId)
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("LISTENING", foundSession.get("status").asText());
        assertEquals("GENERAL_FINANCE", foundSession.get("entryPoint").asText());
        assertTrue(foundSession.get("navigation").isNull());

        JsonNode closedSession = response(mockMvc.perform(post("/api/voice/sessions/{sessionId}/close", sessionId)
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("CLOSED", closedSession.get("status").asText());
        assertFalse(closedSession.get("endedAt").isNull());

        JsonNode foundClosedSession = response(mockMvc.perform(get("/api/voice/sessions/{sessionId}", sessionId)
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("CLOSED", foundClosedSession.get("status").asText());
    }

    @Test
    void createsBillPaymentSessionWithCameraNavigation() throws Exception {
        JsonNode createdSession = response(mockMvc.perform(post("/api/voice/sessions")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryPoint\":\"BILL_PAYMENT\"}"))
                .andExpect(status().isCreated()));
        String sessionId = createdSession.get("sessionId").asText();

        assertEquals("BILL_PAYMENT", createdSession.get("entryPoint").asText());
        assertEquals("GENERAL_FINANCE", createdSession.get("flowType").asText());
        assertEquals("CLIENT", createdSession.get("sttMode").asText());
        assertEquals("BILL_CAMERA", createdSession.get("navigation").get("screenCode").asText());
        assertEquals(sessionId,
                createdSession.get("navigation").get("voiceSessionId").asText());
        assertEquals("고지서를 화면 안에 맞춰 촬영해주세요.",
                createdSession.get("firstPrompt").asText());

        JsonNode foundSession = response(mockMvc.perform(get("/api/voice/sessions/{sessionId}", sessionId)
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("BILL_PAYMENT", foundSession.get("entryPoint").asText());
        assertEquals("BILL_CAMERA", foundSession.get("navigation").get("screenCode").asText());
        assertEquals(sessionId, foundSession.get("navigation").get("voiceSessionId").asText());
    }

    @Test
    void rejectsSessionCreationWithoutEntryPoint() throws Exception {
        JsonNode response = response(mockMvc.perform(post("/api/voice/sessions")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()));

        assertEquals("INVALID_REQUEST", response.get("code").asText());
    }

    @Test
    void returnsStandardBadRequestForInvalidVoiceSettings() throws Exception {
        JsonNode emptyRequest = response(mockMvc.perform(put("/api/users/me/voice-settings")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()));
        assertEquals("INVALID_REQUEST", emptyRequest.get("code").asText());

        JsonNode invalidRange = response(mockMvc.perform(put("/api/users/me/voice-settings")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speechRateMultiplier\":1.21}"))
                .andExpect(status().isBadRequest()));
        assertEquals("INVALID_REQUEST", invalidRange.get("code").asText());
    }

    @Test
    void returnsStandardUnauthorizedResponseWithoutJwt() throws Exception {
        JsonNode response = response(mockMvc.perform(get("/api/users/me/voice-settings"))
                .andExpect(status().isUnauthorized()));

        assertEquals("AUTHENTICATION_REQUIRED", response.get("code").asText());
    }

    @Test
    void hidesOtherUsersAndMissingSessionsWithTheSameNotFoundResponse() throws Exception {
        JsonNode createdSession = response(mockMvc.perform(post("/api/voice/sessions")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryPoint\":\"GENERAL_FINANCE\"}"))
                .andExpect(status().isCreated()));
        String sessionId = createdSession.get("sessionId").asText();

        JsonNode otherUsersSession = response(mockMvc.perform(get("/api/voice/sessions/{sessionId}", sessionId)
                        .header("Authorization", authorizationFor(OTHER_USER_ID)))
                .andExpect(status().isNotFound()));
        JsonNode missingSession = response(mockMvc.perform(get("/api/voice/sessions/{sessionId}", UUID.randomUUID())
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isNotFound()));

        assertEquals("VOICE_SESSION_NOT_FOUND", otherUsersSession.get("code").asText());
        assertEquals(otherUsersSession.get("code").asText(), missingSession.get("code").asText());
    }

    @Test
    void expiresAnElapsedSessionAndKeepsTheExpiredStateWhenClosingIt() throws Exception {
        UUID sessionId = UUID.randomUUID();
        VoiceSessionVo expiredSession = new VoiceSessionVo();
        expiredSession.setSessionId(sessionId.toString());
        expiredSession.setUserId(USER_ID.toString());
        expiredSession.setStatus(VoiceSessionStatus.LISTENING.name());
        expiredSession.setCurrentStep(DialogueStep.AWAITING_INPUT.name());
        expiredSession.setFlowType(VoiceFlowType.GENERAL_FINANCE.name());
        expiredSession.setSttMode(SttMode.CLIENT.name());
        expiredSession.setEntryPoint(VoiceSessionEntryPoint.GENERAL_FINANCE.name());
        expiredSession.setStartedAt(LocalDateTime.of(2026, 9, 2, 9, 0));
        expiredSession.setExpiresAt(LocalDateTime.of(2026, 9, 2, 9, 59));
        voiceSessionMapper.insert(expiredSession);

        JsonNode expired = response(mockMvc.perform(get("/api/voice/sessions/{sessionId}", sessionId)
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("EXPIRED", expired.get("status").asText());

        JsonNode closedExpiredSession = response(mockMvc.perform(post("/api/voice/sessions/{sessionId}/close", sessionId)
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("EXPIRED", closedExpiredSession.get("status").asText());
    }

    @Test
    void persistsAndReusesAnAuthenticatedGeneralFinanceTurn() throws Exception {
        JsonNode createdSession = response(mockMvc.perform(post("/api/voice/sessions")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryPoint\":\"GENERAL_FINANCE\"}"))
                .andExpect(status().isCreated()));
        String sessionId = createdSession.get("sessionId").asText();
        String turnId = UUID.randomUUID().toString();
        String requestBody = "{\"turnId\":\"" + turnId + "\","
                + "\"transcript\":\"내 계좌 잔액을 알려줘\","
                + "\"sttConfidence\":0.96,\"inputType\":\"VOICE\"}";

        JsonNode firstResponse = response(mockMvc.perform(post("/api/voice/sessions/{sessionId}/turns", sessionId)
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk()));
        assertEquals("AWAITING_INPUT", firstResponse.get("state").asText());
        assertEquals("ACCOUNT_INQUIRY", firstResponse.get("intent").asText());
        assertEquals("ACCOUNT_INQUIRY", firstResponse.get("requestedFunction").asText());
        assertEquals("PRESENT_RESULT", firstResponse.get("nextAction").asText());
        assertEquals("balance", firstResponse.get("slots").get("query").asText());
        assertEquals(1, firstResponse.get("slots").get("accountCount").asInt());
        assertEquals(0.96, firstResponse.get("confidence").asDouble());
        assertEquals("생활비 통장의 잔액은 48,200원입니다.", firstResponse.get("ttsText").asText());
        assertTrue(firstResponse.has("ttsSsml"));
        assertEquals("ACCOUNT_LIST", firstResponse.get("displayCard").get("type").asText());
        assertEquals("ACCOUNT_LIST", firstResponse.get("displayCard").get("screenCode").asText());
        assertTrue(firstResponse.get("displayCard").get("actions").isArray());
        assertEquals(0, firstResponse.get("displayCard").get("actions").size());
        assertEquals("1234-****-****-5678", firstResponse.get("displayCard")
                .get("items").get(0).get("accountNumberMasked").asText());

        JsonNode repeatedResponse = response(mockMvc.perform(post("/api/voice/sessions/{sessionId}/turns", sessionId)
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk()));
        assertEquals(firstResponse, repeatedResponse);
        DialogueTurnVo storedUserTurn = dialogueTurnMapper.findBySessionIdAndTurnId(sessionId, turnId);
        assertEquals("USER", storedUserTurn.getSpeaker());
        int aiSequenceNo = storedUserTurn.getSequenceNo() + 1;
        assertEquals("AI", dialogueTurnMapper.findBySessionIdAndSequenceNo(sessionId, aiSequenceNo).getSpeaker());
        assertNull(dialogueTurnMapper.findBySessionIdAndSequenceNo(sessionId, aiSequenceNo + 1));

        JsonNode speakingSession = response(mockMvc.perform(get("/api/voice/sessions/{sessionId}", sessionId)
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("SPEAKING", speakingSession.get("status").asText());
        assertEquals("AWAITING_INPUT", speakingSession.get("currentStep").asText());
    }

    @Test
    void returnsConflictAndRestoresTheSessionWhenTurnIdExistsInAnotherSession() throws Exception {
        String turnId = UUID.randomUUID().toString();
        String requestBody = "{\"turnId\":\"" + turnId + "\","
                + "\"transcript\":\"내 계좌 잔액을 알려줘\","
                + "\"sttConfidence\":0.96,\"inputType\":\"VOICE\"}";

        JsonNode firstSession = response(mockMvc.perform(post("/api/voice/sessions")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryPoint\":\"GENERAL_FINANCE\"}"))
                .andExpect(status().isCreated()));
        response(mockMvc.perform(post("/api/voice/sessions/{sessionId}/turns", firstSession.get("sessionId").asText())
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk()));

        JsonNode secondSession = response(mockMvc.perform(post("/api/voice/sessions")
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryPoint\":\"GENERAL_FINANCE\"}"))
                .andExpect(status().isCreated()));
        String secondSessionId = secondSession.get("sessionId").asText();

        JsonNode conflict = response(mockMvc.perform(post("/api/voice/sessions/{sessionId}/turns", secondSessionId)
                        .header("Authorization", authorizationFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict()));
        assertEquals("VOICE_TURN_CONFLICT", conflict.get("code").asText());
        assertNull(dialogueTurnMapper.findBySessionIdAndTurnId(secondSessionId, turnId));

        JsonNode restoredSession = response(mockMvc.perform(get("/api/voice/sessions/{sessionId}", secondSessionId)
                        .header("Authorization", authorizationFor(USER_ID)))
                .andExpect(status().isOk()));
        assertEquals("LISTENING", restoredSession.get("status").asText());
    }

    private VoiceTurnAnalysisResult accountInquiryAnalysis() {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                VoiceIntent.ACCOUNT_INQUIRY,
                java.util.Map.of("query", "balance"),
                new java.math.BigDecimal("0.96"),
                "계좌 잔액을 확인해 드릴게요.",
                "<speak>계좌 잔액을 확인해 드릴게요.</speak>",
                objectMapper.valueToTree(java.util.Map.of("type", "account")),
                null,
                null,
                VoiceNextAction.PRESENT_RESULT,
                VoiceRequestedFunction.ACCOUNT_INQUIRY);
    }

    private AccountResponse accountResponse() {
        return new AccountResponse(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "004",
                "1234-****-****-5678",
                "생활비 통장",
                48200L,
                "SAVINGS",
                true,
                OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
    }

    private SqlSessionFactory newSessionFactory() throws Exception {
        dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Configuration configuration = new Configuration(
                new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(UserVoiceSettingsMapper.class);
        configuration.addMapper(VoiceSessionMapper.class);
        configuration.addMapper(DialogueTurnMapper.class);
        parseMapper(configuration, "mapper/voice/UserVoiceSettingsMapper.xml");
        parseMapper(configuration, "mapper/voice/VoiceSessionMapper.xml");
        parseMapper(configuration, "mapper/voice/DialogueTurnMapper.xml");
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void parseMapper(Configuration configuration, String resourcePath) throws Exception {
        try (Reader mapperXml = Resources.getResourceAsReader(resourcePath)) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    mapperXml, configuration, resourcePath, configuration.getSqlFragments()).parse();
        }
    }

    private void createSchema(SqlSessionFactory sessionFactory) throws Exception {
        try (SqlSession session = sessionFactory.openSession(true)) {
            session.getConnection().createStatement().execute("CREATE TABLE user_voice_settings ("
                    + "user_id CHAR(36) PRIMARY KEY, voice_name VARCHAR(100) NOT NULL, "
                    + "speech_rate_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.05, "
                    + "volume_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.00, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            session.getConnection().createStatement().execute("CREATE TABLE voice_sessions ("
                    + "session_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, "
                    + "from_account_id CHAR(36) NULL, transfer_id CHAR(36) NULL, "
                    + "status VARCHAR(20) NOT NULL, current_step VARCHAR(30) NOT NULL, "
                    + "flow_type VARCHAR(30) NOT NULL, stt_mode VARCHAR(20) NOT NULL, "
                    + "entry_point VARCHAR(30) NOT NULL, "
                    + "started_at TIMESTAMP NOT NULL, ended_at TIMESTAMP NULL, expires_at TIMESTAMP NOT NULL)");
            session.getConnection().createStatement().execute("CREATE TABLE dialogue_turns ("
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

    @SuppressWarnings("unchecked")
    private <T> T autocommitMapper(SqlSessionFactory sessionFactory, Class<T> mapperType) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[] {mapperType},
                (proxy, method, arguments) -> {
                    try (SqlSession session = sessionFactory.openSession(true)) {
                        return method.invoke(session.getMapper(mapperType), arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private String authorizationFor(UUID userId) {
        return "Bearer " + jwtTokenProvider.issue(userId).getAccessToken();
    }

    private JsonNode response(ResultActions resultActions) throws Exception {
        return objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsByteArray());
    }
}
