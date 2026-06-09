package com.platform.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.search.app.SearchService;
import com.platform.support.PostgresContainerSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class VectorSearchIT extends PostgresContainerSupport {

    @Autowired
    SearchService searchService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void indexQuestion_createsCorpusChunkWithEmbedding() {
        UUID id = UUID.randomUUID();
        searchService.indexQuestion(id, "math", "Calculus fundamentals", "Integration and differentiation");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM corpus_chunk WHERE question_id = ? AND embedding IS NOT NULL",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void hybridSearch_returnsIndexedQuestion() {
        UUID id = UUID.randomUUID();
        searchService.indexQuestion(id, "physics", "Newtonian mechanics", "Force mass acceleration");

        List<UUID> results = searchService.hybridSearch("mechanics force", 5);
        assertThat(results).contains(id);
    }
}
