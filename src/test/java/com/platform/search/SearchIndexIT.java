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
class SearchIndexIT extends PostgresContainerSupport {

    @Autowired
    SearchService searchService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void indexesQuestionAndSearchFindsIt() {
        UUID id = UUID.randomUUID();
        searchService.indexQuestion(id, "math", "What is two plus two", "Add the numbers");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM corpus_index WHERE question_id = ? AND ts_content IS NOT NULL",
                Integer.class, id);
        assertThat(count).isEqualTo(1);

        List<UUID> results = searchService.search("plus two", 5);
        assertThat(results).contains(id);
    }
}
