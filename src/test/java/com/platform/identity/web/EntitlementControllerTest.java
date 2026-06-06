package com.platform.identity.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.identity.app.EntitlementService;
import com.platform.identity.domain.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EntitlementController.class)
class EntitlementControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EntitlementService entitlements;

    @Test
    @WithMockUser(username = "stu@example.com")
    void returnsAllowedTrueForGrantedFeature() throws Exception {
        when(entitlements.check("stu@example.com", Feature.AI_ANSWER)).thenReturn(true);

        mockMvc.perform(get("/api/entitlements/check").param("feature", "AI_ANSWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feature").value("AI_ANSWER"))
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    @WithMockUser(username = "stu@example.com")
    void returnsAllowedFalseForDeniedFeature() throws Exception {
        when(entitlements.check("stu@example.com", Feature.POST_QUESTION)).thenReturn(false);

        mockMvc.perform(get("/api/entitlements/check").param("feature", "POST_QUESTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/entitlements/check").param("feature", "POST_QUESTION"))
                .andExpect(status().isUnauthorized());
    }
}
