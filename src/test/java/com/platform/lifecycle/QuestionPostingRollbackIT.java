package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.platform.lifecycle.app.PostQuestionCommand;
import com.platform.lifecycle.app.QuestionPostingService;
import com.platform.shared.outbox.OutboxStore;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * If the outbox write fails, the question and its audit row must not commit. Proves the posting
 * transaction wraps all three writes (Slice 03 acceptance: tx rollback test).
 */
@SpringBootTest
class QuestionPostingRollbackIT extends PostgresContainerSupport {

    @MockitoBean
    OutboxStore outbox;

    @Autowired
    QuestionPostingService posting;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void outboxFailureRollsBackTheQuestionAndItsEvent() {
        doThrow(new RuntimeException("outbox unavailable")).when(outbox).append(any());
        UUID studentId = UUID.randomUUID();
        Integer eventsBefore = jdbc.queryForObject("SELECT count(*) FROM question_events", Integer.class);

        assertThatThrownBy(() -> posting.post(new PostQuestionCommand(
                studentId, "math", "Title", "Body", Instant.parse("2030-01-01T00:00:00Z"))))
                .isInstanceOf(RuntimeException.class);

        Integer questions = jdbc.queryForObject(
                "SELECT count(*) FROM questions WHERE student_id = ?", Integer.class, studentId);
        assertThat(questions).isEqualTo(0);
        Integer eventsAfter = jdbc.queryForObject("SELECT count(*) FROM question_events", Integer.class);
        assertThat(eventsAfter).isEqualTo(eventsBefore);
    }
}
