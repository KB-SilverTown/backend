package com.silvertown.domain.mobilebranch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationQuery;
import com.silvertown.domain.mobilebranch.service.MobileBranchService;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.security.AuthenticatedUserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobileBranchControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private MobileBranchService mobileBranchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mobileBranchService = Mockito.mock(MobileBranchService.class);
        when(mobileBranchService.recommend(any())).thenReturn(List.of());
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MobileBranchController(mobileBranchService, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void forwardsCapacitorCoordinatesToTheNearbyRecommendationService() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/mobile-branches/nearby")
                        .param("latitude", "37.5012345")
                        .param("longitude", "127.0398765")
                        .accept(MediaType.APPLICATION_JSON)
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("[]", result.getResponse().getContentAsString());

        ArgumentCaptor<MobileBranchRecommendationQuery> queryCaptor =
                ArgumentCaptor.forClass(MobileBranchRecommendationQuery.class);
        verify(mobileBranchService).recommend(queryCaptor.capture());
        assertEquals(37.5012345, queryCaptor.getValue().getLatitude());
        assertEquals(127.0398765, queryCaptor.getValue().getLongitude());
        assertNull(queryCaptor.getValue().getServiceCode());
    }

    @Test
    void requiresBothLocationParameters() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/mobile-branches/nearby")
                        .param("latitude", "37.5012345")
                        .principal(authentication()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertTrue(result.getResponse().getContentAsString().contains("\"code\":\"INVALID_REQUEST\""));
    }

    @Test
    void returnsInvalidRequestWhenLatitudeIsNotNumeric() throws Exception {
        assertInvalidCoordinate("latitude", "not-a-number");
    }

    @Test
    void returnsInvalidRequestWhenLongitudeIsNotNumeric() throws Exception {
        assertInvalidCoordinate("longitude", "not-a-number");
    }

    private void assertInvalidCoordinate(String invalidParameter, String invalidValue) throws Exception {
        String latitude = "latitude".equals(invalidParameter) ? invalidValue : "37.5012345";
        String longitude = "longitude".equals(invalidParameter) ? invalidValue : "127.0398765";
        var request = get("/api/mobile-branches/nearby")
                .param("latitude", latitude)
                .param("longitude", longitude)
                .accept(MediaType.APPLICATION_JSON)
                .principal(authentication());

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertTrue(result.getResponse().getContentAsString().contains("\"code\":\"INVALID_REQUEST\""));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(USER_ID.toString(), "", List.of());
    }
}
