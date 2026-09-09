package com.silvertown.domain.voice.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silvertown.domain.voice.service.VoiceStreamTicketService;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

class VoiceStreamHandshakeInterceptorTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String TICKET = "vst_opaque-ticket";

    private VoiceStreamTicketService voiceStreamTicketService;
    private VoiceStreamHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        voiceStreamTicketService = Mockito.mock(VoiceStreamTicketService.class);
        interceptor = new VoiceStreamHandshakeInterceptor(voiceStreamTicketService);
    }

    @Test
    void consumesTicketAndSetsTicketUserAsHandshakePrincipal() throws Exception {
        when(voiceStreamTicketService.consumeForHandshake(SESSION_ID, TICKET))
                .thenReturn(Optional.of(USER_ID));
        MockHttpServletRequest request = streamRequest("voice-stream-v1, ticket." + TICKET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HashMap<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response),
                Mockito.mock(WebSocketHandler.class), attributes));

        VoiceStreamPrincipal principal = (VoiceStreamPrincipal) attributes.get(
                VoiceStreamPrincipal.HANDSHAKE_ATTRIBUTE);
        assertEquals(USER_ID, principal.getName());
        verify(voiceStreamTicketService).consumeForHandshake(SESSION_ID, TICKET);
    }

    @Test
    void rejectsHandshakeWithoutTheRequiredProtocolAndDoesNotConsumeTicket() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.beforeHandshake(
                new ServletServerHttpRequest(streamRequest("ticket." + TICKET)),
                new ServletServerHttpResponse(response), Mockito.mock(WebSocketHandler.class), new HashMap<>()));

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        verify(voiceStreamTicketService, Mockito.never()).consumeForHandshake(
                Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void rejectsExpiredOrPreviouslyConsumedTicketBeforeHandlerEntry() throws Exception {
        when(voiceStreamTicketService.consumeForHandshake(SESSION_ID, TICKET)).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.beforeHandshake(
                new ServletServerHttpRequest(streamRequest("voice-stream-v1", "ticket." + TICKET)),
                new ServletServerHttpResponse(response), Mockito.mock(WebSocketHandler.class), new HashMap<>()));

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
    }

    private MockHttpServletRequest streamRequest(String... protocols) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/voice/sessions/" + SESSION_ID + "/stream");
        request.addHeader("Sec-WebSocket-Protocol", String.join(", ", protocols));
        return request;
    }
}
