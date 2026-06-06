package com.platform.identity.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.identity.app.RegistrationService;
import com.platform.identity.domain.Role;
import com.platform.identity.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegistrationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RegistrationService registration;

    @Test
    void registersAndReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(registration.register(eq("a@b.com"), eq("password123"), eq(Role.STUDENT)))
                .thenReturn(new User(id, "a@b.com", "h", Role.STUDENT, Instant.EPOCH));

        mockMvc.perform(post("/api/identity/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"password123\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("a@b.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void rejectsInvalidEmailWith400() throws Exception {
        mockMvc.perform(post("/api/identity/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"password123\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isBadRequest());
    }
}
