package com.wallet.rewards.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.rewards.entity.RewardCatalog;
import com.wallet.rewards.entity.RewardPoints;
import com.wallet.rewards.service.RewardsService;
import com.wallet.rewards.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RewardsController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RewardsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RewardsService rewardsService;

    @MockBean
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getSummary_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        RewardPoints points = new RewardPoints();
        points.setUserId(userId);
        points.setTotalPoints(100);

        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(rewardsService.getSummary(userId)).thenReturn(points);

        mockMvc.perform(get("/api/rewards/summary")
                .header("Authorization", "Bearer testToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(100));
    }

    @Test
    void getCatalog_Success() throws Exception {
        RewardCatalog item = new RewardCatalog();
        item.setName("Gift Card");
        
        when(rewardsService.getCatalog()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/rewards/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Gift Card"));
    }

    @Test
    void redeem_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(rewardsService.redeemItem(userId, catalogId)).thenReturn("Success");

        mockMvc.perform(post("/api/rewards/redeem/" + catalogId)
                .header("Authorization", "Bearer testToken"))
                .andExpect(status().isOk())
                .andExpect(content().string("Success"));
    }

    @Test
    void redeem_Failure() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(rewardsService.redeemItem(any(), any())).thenThrow(new RuntimeException("Insufficient points"));

        mockMvc.perform(post("/api/rewards/redeem/" + catalogId)
                .header("Authorization", "Bearer testToken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Insufficient points"));
    }

    @Test
    void createCatalogItem_Success() throws Exception {
        RewardCatalog item = new RewardCatalog();
        item.setName("Headphones");
        item.setDescription("Wireless over-ear headphones");
        item.setCostInPoints(200);
        item.setStockQuantity(5);
        item.setRequiredTier("ALL");

        when(rewardsService.createCatalogItem(any(RewardCatalog.class))).thenReturn(item);

        mockMvc.perform(post("/api/rewards/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Headphones"));
    }
}
