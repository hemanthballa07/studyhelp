package com.platform.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.lifecycle.app.PostQuestionCommand;
import com.platform.lifecycle.app.QuestionPostingService;
import com.platform.lifecycle.app.QuestionRoutingService;
import com.platform.search.app.SearchService;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DedupShortCircuitIT extends PostgresContainerSupport {

    @Autowired
    QuestionPostingService posting;

    @Autowired
    SearchService searchService;

    @Autowired
    QuestionRoutingService routing;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void nearDuplicateShortCircuitsToDelivered() {
        String subject = "calculus";
        String titleA = "How do you find the derivative of x squared plus 3x using the power rule";
        String bodyA = "Apply the power rule to each term: bring down the exponent and subtract one "
                + "from it. The derivative of x squared is 2x and the derivative of 3x is 3 so the "
                + "result is 2x plus 3";

        UUID idA = posting.post(new PostQuestionCommand(
                UUID.randomUUID(), subject, titleA, bodyA, Instant.parse("2030-01-01T00:00:00Z")));
        searchService.indexQuestion(idA, subject, titleA, bodyA);

        String titleB = "What is the derivative of x squared plus 3x using power rule steps";
        String bodyB = "Using the power rule on each term bring down the exponent and reduce it by one. "
                + "For x squared the derivative is 2x and for 3x the derivative is 3 giving 2x plus 3";

        UUID idB = posting.post(new PostQuestionCommand(
                UUID.randomUUID(), subject, titleB, bodyB, Instant.parse("2030-01-01T00:00:00Z")));

        routing.route(idB, subject, titleB, bodyB);

        String stateB = jdbc.queryForObject(
                "SELECT state::text FROM questions WHERE id = ?", String.class, idB);
        assertThat(stateB).isEqualTo("DELIVERED");

        Integer dupEvents = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type = 'DuplicateDetected'",
                Integer.class, idB);
        assertThat(dupEvents).isEqualTo(1);
    }

    @Test
    void noFalsePositiveWhenNoSimilarQuestion() {
        String subject = "history";
        String title = "What caused the French Revolution";
        String body = "Economic hardship food shortages and Enlightenment ideas led the Third Estate "
                + "to revolt against the monarchy in 1789";

        UUID idC = posting.post(new PostQuestionCommand(
                UUID.randomUUID(), subject, title, body, Instant.parse("2030-01-01T00:00:00Z")));

        routing.route(idC, subject, title, body);

        String state = jdbc.queryForObject(
                "SELECT state::text FROM questions WHERE id = ?", String.class, idC);
        assertThat(state).isEqualTo("CLAIMABLE");
    }
}
