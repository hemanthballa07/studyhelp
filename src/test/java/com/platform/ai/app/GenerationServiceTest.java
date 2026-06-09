package com.platform.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.GenerationRepository;
import com.platform.shared.generation.AnswerStep;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.generation.ContextChunk;
import com.platform.shared.generation.GenerationPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GenerationServiceTest {

    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final GenerationPort port = mock(GenerationPort.class);
    private final GenerationRepository repo = mock(GenerationRepository.class);
    private final GenerationService service = new GenerationService(retrieval, port, repo);

    @Test
    void generatedStepsCarryChunkCitations() {
        UUID chunkId = UUID.randomUUID();
        AiCorpusChunk chunk = new AiCorpusChunk(chunkId, "src", "lic", "Newton's second law: F = ma");
        when(retrieval.retrieve(anyString(), anyInt())).thenReturn(List.of(chunk));

        CandidateAnswer expected = new CandidateAnswer(List.of(
                new AnswerStep("F = ma is Newton's second law.", List.of(chunkId))));
        when(port.generate(anyString(), any())).thenReturn(expected);

        CandidateAnswer result = service.generate(UUID.randomUUID(), "What is Newton's second law?");

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).citationChunkIds()).containsExactly(chunkId);
        assertThat(result.isFullyCited()).isTrue();
    }

    @Test
    void contextCapAppliedBeforeGeneration() {
        when(retrieval.retrieve(anyString(), anyInt())).thenReturn(List.of(
                new AiCorpusChunk(UUID.randomUUID(), "s", "l", "c1"),
                new AiCorpusChunk(UUID.randomUUID(), "s", "l", "c2"),
                new AiCorpusChunk(UUID.randomUUID(), "s", "l", "c3"),
                new AiCorpusChunk(UUID.randomUUID(), "s", "l", "c4"),
                new AiCorpusChunk(UUID.randomUUID(), "s", "l", "c5")));
        when(port.generate(anyString(), any())).thenReturn(
                new CandidateAnswer(List.of(new AnswerStep("ok", List.of(UUID.randomUUID())))));

        service.generate(UUID.randomUUID(), "question");

        ArgumentCaptor<List<ContextChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(port).generate(anyString(), captor.capture());
        assertThat(captor.getValue()).hasSizeLessThanOrEqualTo(GenerationService.MAX_CONTEXT_CHUNKS);
    }

    @Test
    void uncitedClaimDetectable() {
        when(retrieval.retrieve(anyString(), anyInt())).thenReturn(List.of());
        when(port.generate(anyString(), any())).thenReturn(
                new CandidateAnswer(List.of(new AnswerStep("no citation", List.of()))));

        CandidateAnswer result = service.generate(UUID.randomUUID(), "question");

        assertThat(result.hasUncitedClaims()).isTrue();
        assertThat(result.isFullyCited()).isFalse();
    }

    @Test
    void answerPersistedAfterGeneration() {
        when(retrieval.retrieve(anyString(), anyInt())).thenReturn(List.of());
        when(port.generate(anyString(), any())).thenReturn(
                new CandidateAnswer(List.of(new AnswerStep("step", List.of()))));

        service.generate(UUID.randomUUID(), "question");

        verify(repo).save(any(UUID.class), any(UUID.class), any(CandidateAnswer.class));
    }
}
