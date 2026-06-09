package com.platform.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.search.app.SearchService;
import com.platform.search.domain.SearchRepository;
import com.platform.shared.embedding.EmbeddingPort;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Adversarial test for RRF fusion: stubs FTS=[A,B,C] and vector=[C,A,D] and asserts the
 * fused ranking is A > C > B > D based on the formula score = 1/(60+rank_fts) + 1/(60+rank_vector).
 */
@ExtendWith(MockitoExtension.class)
class RrfFusionTest {

    @Mock SearchRepository repo;
    @Mock OutboxStore outbox;
    @Mock EmbeddingPort embeddingPort;
    @Mock Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rrfFusesAndRanksCorrectly() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        when(repo.search(any(), anyInt())).thenReturn(List.of(a, b, c));
        when(embeddingPort.embed(any())).thenReturn(new float[384]);
        when(repo.findSimilar(any(), anyInt())).thenReturn(List.of(c, a, d));

        SearchService svc = new SearchService(repo, outbox, clock, objectMapper, embeddingPort);
        List<UUID> result = svc.hybridSearch("calculus", 10);

        // A: 1/61 + 1/62 = ~0.03252  (rank 1 FTS, rank 2 vector)
        // C: 1/63 + 1/61 = ~0.03226  (rank 3 FTS, rank 1 vector)
        // B: 1/62 + 0    = ~0.01613  (rank 2 FTS, absent vector)
        // D: 0    + 1/63 = ~0.01587  (absent FTS, rank 3 vector)
        assertThat(result.indexOf(a)).isLessThan(result.indexOf(c));
        assertThat(result.indexOf(c)).isLessThan(result.indexOf(b));
        assertThat(result.indexOf(b)).isLessThan(result.indexOf(d));
    }
}
