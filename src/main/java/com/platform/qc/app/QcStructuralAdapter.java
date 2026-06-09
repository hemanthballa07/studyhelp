package com.platform.qc.app;

import com.platform.shared.qc.StructuralQcPort;
import org.springframework.stereotype.Component;

/** Adapts QcRubricScorer to the shared StructuralQcPort so ai can call it without importing qc. */
@Component
public class QcStructuralAdapter implements StructuralQcPort {

    private final QcRubricScorer scorer;

    public QcStructuralAdapter(QcRubricScorer scorer) {
        this.scorer = scorer;
    }

    @Override
    public double score(String answerText, String subjectCode) {
        return Math.min(1.0, scorer.score(answerText, subjectCode).totalScore() / 100.0);
    }
}
