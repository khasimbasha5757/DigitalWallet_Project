package com.wallet.admin.controller;

import com.wallet.admin.entity.Campaign;
import com.wallet.admin.repository.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CampaignRepository campaignRepository;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void getDashboardMetrics_Success() throws Exception {
        when(campaignRepository.count()).thenReturn(5L);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCampaigns").value(5))
                .andExpect(jsonPath("$.status").value("Operational"));
    }

    @Test
    void createCampaign_Success() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setName("Summer Promo");
        
        when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

        mockMvc.perform(post("/api/admin/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Summer Promo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Summer Promo"));
    }

    @Test
    void approveKyc_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "Bearer testToken";
        
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(java.util.Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("email", "user@gmail.com", "status", "APPROVED"), HttpStatus.OK));

        mockMvc.perform(post("/api/admin/kyc/" + userId + "/approve")
                .header("Authorization", token)
                .param("documentReviewed", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KYC Approved")));
    }

    @Test
    void approveKyc_RequiresDocumentReview() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/kyc/" + userId + "/approve")
                .header("Authorization", "Bearer testToken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Admin must review")));
    }

    @Test
    void rejectKyc_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "Bearer testToken";
        
        when(restTemplate.postForEntity(any(java.net.URI.class), any(HttpEntity.class), eq(java.util.Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("email", "user@gmail.com", "status", "REJECTED"), HttpStatus.OK));

        mockMvc.perform(post("/api/admin/kyc/" + userId + "/reject")
                .header("Authorization", token)
                .param("reason", "Invalid ID"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KYC Rejected")));
    }

    @Test
    void approveKyc_InvalidEmail_ReturnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(java.util.Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "APPROVED"), HttpStatus.OK));
        when(restTemplate.exchange(any(String.class), eq(org.springframework.http.HttpMethod.GET), any(HttpEntity.class), eq(java.util.Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("email", "invalid-email"), HttpStatus.OK));

        mockMvc.perform(post("/api/admin/kyc/" + userId + "/approve")
                .header("Authorization", "Bearer testToken")
                .param("documentReviewed", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unable to resolve a valid email address")));
    }

    @Test
    void getPendingKycs_Success() throws Exception {
        String token = "Bearer testToken";
        List<String> mockList = List.of("user1", "user2");
        
        when(restTemplate.exchange(any(String.class), eq(org.springframework.http.HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
                .thenReturn(new ResponseEntity<>(mockList, HttpStatus.OK));

        mockMvc.perform(get("/api/admin/kyc/pending")
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("user1"));
    }
}
