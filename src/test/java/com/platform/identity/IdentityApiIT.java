package com.platform.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityApiIT extends PostgresContainerSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    void registersStudentThenChecksEntitlements() throws Exception {
        mockMvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stu1@x.com\",\"password\":\"password123\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"));

        mockMvc.perform(get("/api/entitlements/check").param("feature", "POST_QUESTION")
                        .with(jwt().jwt(token -> token.subject("stu1@x.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(get("/api/entitlements/check").param("feature", "AI_ANSWER")
                        .with(jwt().jwt(token -> token.subject("stu1@x.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void duplicateRegistrationConflicts() throws Exception {
        String body = "{\"email\":\"dup@x.com\",\"password\":\"password123\",\"role\":\"EXPERT\"}";
        mockMvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void adminEndpointEnforcesRole() throws Exception {
        mockMvc.perform(get("/api/admin/users/count")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users/count")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users/count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
