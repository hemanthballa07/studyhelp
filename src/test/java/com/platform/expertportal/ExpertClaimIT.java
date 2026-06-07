package com.platform.expertportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.support.PostgresContainerSupport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * End-to-end claim through {@code POST /api/claims}: a win delivers the question and emits
 * {@code QuestionClaimed}; a miss returns 204 and emits {@code QuestionSkipped} with no state change;
 * an expert not registered for the subject is rejected. The expert is taken from the principal's
 * {@code userId} claim. Each test uses a subject unique to itself to stay isolated on the shared
 * Postgres container.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExpertClaimIT extends PostgresContainerSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void claimingWinsTheQuestionLeavesTheClaimablePoolAndEmitsQuestionClaimed() throws Exception {
        UUID expertId = UUID.randomUUID();
        String subject = uniqueSubject();
        registerExpert(expertId, subject);
        UUID questionId = insertClaimable(subject);

        mockMvc.perform(post("/api/claims")
                        .with(expertJwt(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"" + subject + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(questionId.toString()))
                .andExpect(jsonPath("$.claimedBy").value(expertId.toString()));

        assertThat(state(questionId)).as("the claimed question left the claimable pool").isEqualTo("CLAIMED");
        assertThat(claimAttempts(expertId, "WON")).as("the win is logged").isEqualTo(1);
        assertThat(outboxFor(questionId, "QuestionClaimed")).as("QuestionClaimed is in the outbox").isEqualTo(1);
        assertThat(outboxBySubject(subject, "ExpertClaimAttempted")).as("the attempt is published").isEqualTo(1);
    }

    @Test
    void claimingWithNothingClaimableReturns204AndEmitsQuestionSkipped() throws Exception {
        UUID expertId = UUID.randomUUID();
        String subject = uniqueSubject();
        registerExpert(expertId, subject);

        mockMvc.perform(post("/api/claims")
                        .with(expertJwt(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"" + subject + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(claimAttempts(expertId, "SKIPPED")).as("the skip is logged").isEqualTo(1);
        assertThat(outboxBySubject(subject, "QuestionSkipped")).as("QuestionSkipped is in the outbox").isEqualTo(1);
        assertThat(outboxBySubject(subject, "QuestionClaimed"))
                .as("nothing claimed -> no state-change event").isZero();
        assertThat(claimedCount(subject)).as("no question state was changed").isZero();
    }

    @Test
    void claimingASubjectTheExpertDoesNotHandleIsForbidden() throws Exception {
        UUID expertId = UUID.randomUUID();
        String subject = uniqueSubject();
        // deliberately NOT registered for the subject
        insertClaimable(subject);

        mockMvc.perform(post("/api/claims")
                        .with(expertJwt(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"" + subject + "\"}"))
                .andExpect(status().isForbidden());

        assertThat(claimAttempts(expertId, "WON")).isZero();
        assertThat(claimAttempts(expertId, "SKIPPED")).isZero();
        assertThat(claimedCount(subject)).as("ineligible claim never changed state").isZero();
    }

    @Test
    void claimingWithoutTheExpertRoleIsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        String subject = uniqueSubject();

        mockMvc.perform(post("/api/claims")
                        .with(jwt().jwt(token -> token.claim("userId", userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"" + subject + "\"}"))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor expertJwt(UUID expertId) {
        return jwt().jwt(token -> token.claim("userId", expertId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_EXPERT"));
    }

    private void registerExpert(UUID expertId, String subject) {
        jdbc.update("INSERT INTO expert_subjects (expert_id, subject) VALUES (?, ?)", expertId, subject);
    }

    private UUID insertClaimable(String subject) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at)"
                        + " VALUES (?, ?, ?, 'title', 'body', 'CLAIMABLE', ?)",
                id, UUID.randomUUID(), subject, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        return id;
    }

    private String state(UUID questionId) {
        return jdbc.queryForObject("SELECT state::text FROM questions WHERE id = ?", String.class, questionId);
    }

    private int claimAttempts(UUID expertId, String outcome) {
        return count("SELECT count(*) FROM claim_attempts WHERE expert_id = ? AND outcome = ?", expertId, outcome);
    }

    private int outboxFor(UUID aggregateId, String eventType) {
        return count("SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?", aggregateId, eventType);
    }

    private int outboxBySubject(String subject, String eventType) {
        return count("SELECT count(*) FROM outbox WHERE event_type = ? AND payload->>'subject' = ?",
                eventType, subject);
    }

    private int claimedCount(String subject) {
        return count("SELECT count(*) FROM questions WHERE subject = ? AND state = 'CLAIMED'", subject);
    }

    private int count(String sql, Object... args) {
        Integer c = jdbc.queryForObject(sql, Integer.class, args);
        return c == null ? 0 : c;
    }

    private static String uniqueSubject() {
        return "exp-" + UUID.randomUUID();
    }
}
