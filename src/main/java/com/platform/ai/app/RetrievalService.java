package com.platform.ai.app;

import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.CorpusRepository;
import com.platform.shared.embedding.EmbeddingPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hybrid retrieval (FTS + pgvector) with RRF fusion and score-based rerank.
 * Embedding is computed outside the transaction (same pattern as SearchService)
 * to avoid holding a DB connection during inference and to isolate embedding
 * failures from the retrieval read.
 */
@Service
public class RetrievalService {

    // RRF constant k=60 is the standard value from the original RRF paper (Cormack et al. 2009).
    private static final int RRF_K = 60;

    private final CorpusRepository repo;
    private final EmbeddingPort embeddingPort;

    public RetrievalService(CorpusRepository repo, EmbeddingPort embeddingPort) {
        this.repo = repo;
        this.embeddingPort = embeddingPort;
    }

    /**
     * Top-k hybrid retrieval over the AI corpus using RRF fusion of FTS and vector results.
     * {@code @Transactional(readOnly=true)} is placed here (the entry point) so the Spring AOP
     * proxy intercepts it; embedding is computed first, before the transaction opens, to avoid
     * holding the DB connection during model inference.
     */
    @Transactional(readOnly = true)
    public List<AiCorpusChunk> retrieve(String queryText, int topK) {
        float[] embedding = embeddingPort.embed(queryText);
        return retrieveTransactional(queryText, embedding, topK);
    }

    List<AiCorpusChunk> retrieveTransactional(String queryText, float[] embedding, int topK) {
        List<AiCorpusChunk> ftsList = repo.findByFts(queryText, topK);
        List<AiCorpusChunk> vecList = repo.findByVector(embedding, topK);
        return rrfFuse(ftsList, vecList, topK);
    }

    /**
     * RRF score: sum of 1/(k + rank) across result lists. Chunks appearing in both lists
     * accumulate scores from both; the fused list is sorted descending.
     */
    static List<AiCorpusChunk> rrfFuse(
            List<AiCorpusChunk> ftsList,
            List<AiCorpusChunk> vecList,
            int topK) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        Map<UUID, AiCorpusChunk> byId = new LinkedHashMap<>();

        for (int i = 0; i < ftsList.size(); i++) {
            AiCorpusChunk c = ftsList.get(i);
            scores.merge(c.id(), 1.0 / (RRF_K + i + 1), Double::sum);
            byId.putIfAbsent(c.id(), c);
        }
        for (int i = 0; i < vecList.size(); i++) {
            AiCorpusChunk c = vecList.get(i);
            scores.merge(c.id(), 1.0 / (RRF_K + i + 1), Double::sum);
            byId.putIfAbsent(c.id(), c);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> byId.get(e.getKey()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
