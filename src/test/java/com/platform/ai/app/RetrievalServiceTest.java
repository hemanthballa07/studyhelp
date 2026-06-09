package com.platform.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.CorpusRepository;
import com.platform.shared.embedding.EmbeddingPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalServiceTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID D = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private AiCorpusChunk chunk(UUID id) {
        return new AiCorpusChunk(id, "src", "lic", "text-" + id);
    }

    @Test
    void rrfFuse_doublyRankedChunkBeatsOnlyFts() {
        // FTS=[A(rank1), B(rank2), C(rank3)], vector=[C(rank1), A(rank2), D(rank3)]
        // A: 1/61 + 1/62 = 0.03252, C: 1/63 + 1/61 = 0.03252 (tie by sum)
        // B: 1/62 = 0.01613, D: 1/63 = 0.01587
        List<AiCorpusChunk> fts = List.of(chunk(A), chunk(B), chunk(C));
        List<AiCorpusChunk> vec = List.of(chunk(C), chunk(A), chunk(D));

        List<AiCorpusChunk> result = RetrievalService.rrfFuse(fts, vec, 4);

        assertThat(result).hasSize(4);
        assertThat(result.subList(0, 2)).extracting(AiCorpusChunk::id).containsExactlyInAnyOrder(A, C);
        assertThat(result.get(2).id()).isEqualTo(B);
        assertThat(result.get(3).id()).isEqualTo(D);
    }

    @Test
    void rrfFuse_respectsTopK() {
        List<AiCorpusChunk> fts = List.of(chunk(A), chunk(B));
        List<AiCorpusChunk> vec = List.of(chunk(C), chunk(D));

        List<AiCorpusChunk> result = RetrievalService.rrfFuse(fts, vec, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void rrfFuse_emptyVectorList_returnsFtsOnly() {
        List<AiCorpusChunk> fts = List.of(chunk(A), chunk(B));

        List<AiCorpusChunk> result = RetrievalService.rrfFuse(fts, List.of(), 5);

        assertThat(result).extracting(AiCorpusChunk::id).containsExactly(A, B);
    }

    @Test
    void retrieve_delegatesToRepo() {
        CorpusRepository repo = mock(CorpusRepository.class);
        EmbeddingPort ep = mock(EmbeddingPort.class);
        float[] vec = new float[384];
        when(ep.embed("quadratic formula")).thenReturn(vec);
        when(repo.findByFts("quadratic formula", 3)).thenReturn(List.of(chunk(A)));
        when(repo.findByVector(vec, 3)).thenReturn(List.of(chunk(A), chunk(B)));

        RetrievalService svc = new RetrievalService(repo, ep);
        List<AiCorpusChunk> result = svc.retrieveTransactional("quadratic formula", vec, 3);

        assertThat(result).extracting(AiCorpusChunk::id).containsExactly(A, B);
    }
}
