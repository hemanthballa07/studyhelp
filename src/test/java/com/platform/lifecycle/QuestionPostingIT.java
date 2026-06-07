package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.lifecycle.app.PostQuestionCommand;
import com.platform.lifecycle.app.QuestionPostingService;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Posting a question writes the canonical question row, the append-only audit row, and the outbox
 * row in one transaction (Slice 03 acceptance: "Posting writes question + outbox row atomically").
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuestionPostingIT extends PostgresContainerSupport {

    @Autowired
    QuestionPostingService posting;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void postingThroughTheServiceWritesQuestionEventAndOutboxRows() {
        UUID studentId = UUID.randomUUID();
        UUID questionId = posting.post(new PostQuestionCommand(
                studentId, "math", "Limit of sin x over x", "Show the steps", Instant.parse("2030-01-01T00:00:00Z")));

        Integer questions = jdbc.queryForObject(
                "SELECT count(*) FROM questions WHERE id = ? AND state = 'POSTED'", Integer.class, questionId);
        assertThat(questions).isEqualTo(1);

        Integer events = jdbc.queryForObject(
                "SELECT count(*) FROM question_events WHERE question_id = ? AND event_type = 'QuestionPosted'"
                        + " AND from_state IS NULL AND to_state = 'POSTED'",
                Integer.class, questionId);
        assertThat(events).isEqualTo(1);

        Integer outbox = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND aggregate_type = 'Question'"
                        + " AND event_type = 'QuestionPosted' AND published_at IS NULL",
                Integer.class, questionId);
        assertThat(outbox).isEqualTo(1);
    }

    @Test
    void postingThroughTheApiReturns201AndWritesAnOutboxRow() throws Exception {
        UUID studentId = UUID.randomUUID();
        String body = """
                {"studentId":"%s","subject":"physics","title":"Projectile range","body":"Derive it",
                 "deadlineAt":"2030-01-01T00:00:00Z"}
                """.formatted(studentId);

        mockMvc.perform(post("/api/questions").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        Integer outboxForStudent = jdbc.queryForObject(
                "SELECT count(*) FROM outbox o JOIN questions q ON q.id = o.aggregate_id WHERE q.student_id = ?",
                Integer.class, studentId);
        assertThat(outboxForStudent).isEqualTo(1);
    }
}
