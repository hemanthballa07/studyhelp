package com.platform.expertportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.support.PostgresContainerSupport;
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
 * End-to-end submit/start through the expert portal. A valid submit transitions the question to
 * SUBMITTED, persists a non-stale answer, and publishes {@code AnswerSubmitted}; a submit after the
 * lease expired persists a stale answer with NO payout-triggering event and leaves the question
 * unchanged (no ghost delivery, master-design 6.3). Start moves a claimed question to IN_PROGRESS.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnswerSubmitIT extends PostgresContainerSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void submitUnderAValidLeaseDeliversAndPublishesAnswerSubmitted() throws Exception {
        UUID expertId = UUID.randomUUID();
        UUID questionId = seed("IN_PROGRESS", expertId, true);

        mockMvc.perform(post("/api/answers")
                        .with(expertJwt(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\",\"body\":\"the worked solution\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stale").value(false));

        assertThat(stateOf(questionId)).isEqualTo("SUBMITTED");
        assertThat(answerStale(questionId)).isFalse();
        assertThat(answerSubmittedEvents(questionId)).as("a delivered answer publishes AnswerSubmitted").isEqualTo(1);
    }

    @Test
    void submitAfterLeaseExpiryIsStaleWithNoAnswerSubmitted() throws Exception {
        UUID expertId = UUID.randomUUID();
        UUID questionId = seed("IN_PROGRESS", expertId, false);

        mockMvc.perform(post("/api/answers")
                        .with(expertJwt(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\",\"body\":\"too late\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.stale").value(true));

        assertThat(stateOf(questionId)).as("no ghost delivery").isEqualTo("IN_PROGRESS");
        assertThat(answerStale(questionId)).isTrue();
        assertThat(answerSubmittedEvents(questionId)).as("a stale submit triggers nothing downstream").isZero();
    }

    @Test
    void startMovesAClaimedQuestionToInProgress() throws Exception {
        UUID expertId = UUID.randomUUID();
        UUID questionId = seed("CLAIMED", expertId, true);

        mockMvc.perform(post("/api/claims/" + questionId + "/start").with(expertJwt(expertId)))
                .andExpect(status().isNoContent());

        assertThat(stateOf(questionId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void submittingWithoutTheExpertRoleIsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(post("/api/answers")
                        .with(jwt().jwt(token -> token.claim("userId", userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + UUID.randomUUID() + "\",\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor expertJwt(UUID expertId) {
        return jwt().jwt(token -> token.claim("userId", expertId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_EXPERT"));
    }

    private UUID seed(String state, UUID expertId, boolean leaseValid) {
        UUID id = UUID.randomUUID();
        String leaseExpr = leaseValid ? "now() + interval '20 minutes'" : "now() - interval '1 minute'";
        jdbc.update("INSERT INTO questions"
                        + " (id, student_id, subject, title, body, state, claimed_by, claimed_at,"
                        + "  claim_expires_at, deadline_at)"
                        + " VALUES (?, ?, ?, 'title', 'body', CAST(? AS question_state), ?, now(),"
                        + "  " + leaseExpr + ", now() + interval '1 day')",
                id, UUID.randomUUID(), "ans-" + UUID.randomUUID(), state, expertId);
        return id;
    }

    private String stateOf(UUID id) {
        return jdbc.queryForObject("SELECT state::text FROM questions WHERE id = ?", String.class, id);
    }

    private boolean answerStale(UUID questionId) {
        Boolean stale = jdbc.queryForObject(
                "SELECT stale FROM answers WHERE question_id = ?", Boolean.class, questionId);
        return Boolean.TRUE.equals(stale);
    }

    private int answerSubmittedEvents(UUID questionId) {
        Integer c = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE event_type = 'AnswerSubmitted' AND payload->>'questionId' = ?",
                Integer.class, questionId.toString());
        return c == null ? 0 : c;
    }
}
