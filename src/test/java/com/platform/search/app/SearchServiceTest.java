package com.platform.search.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.search.domain.SearchRepository;
import com.platform.shared.embedding.EmbeddingPort;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    SearchRepository repo;

    @Mock
    OutboxStore outbox;

    @Mock
    Clock clock;

    @Mock
    EmbeddingPort embeddingPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void indexQuestion_callsUpsertAndEmitsContentIndexed() {
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(embeddingPort.embed(any())).thenReturn(new float[384]);
        SearchService svc = new SearchService(repo, outbox, clock, objectMapper, embeddingPort);

        UUID id = UUID.randomUUID();
        svc.indexQuestion(id, "math", "What is 2+2", "Basic arithmetic");

        verify(repo).upsert(id, "math", "What is 2+2", "Basic arithmetic");
        verify(repo).upsertChunk(eq(id), any(), any());
        verify(outbox).append(any());
    }

    @Test
    void findDuplicates_returnsEmptyWhenNoMatch() {
        SearchService svc = new SearchService(repo, outbox, clock, objectMapper, embeddingPort);
        UUID id = UUID.randomUUID();
        when(repo.findDuplicates(eq(id), any(), any(), any(), eq(0.1f)))
                .thenReturn(List.of());

        Optional<UUID> result = new SearchDedupAdapter(svc).checkDuplicate(id, "math", "t", "b");

        assertThat(result).isEmpty();
    }

    @Test
    void findDuplicates_returnsFirstMatchWhenFound() {
        SearchService svc = new SearchService(repo, outbox, clock, objectMapper, embeddingPort);
        UUID id = UUID.randomUUID();
        UUID dupId = UUID.randomUUID();
        when(repo.findDuplicates(eq(id), any(), any(), any(), eq(0.1f)))
                .thenReturn(List.of(dupId));

        Optional<UUID> result = new SearchDedupAdapter(svc).checkDuplicate(id, "math", "t", "b");

        assertThat(result).contains(dupId);
    }
}
