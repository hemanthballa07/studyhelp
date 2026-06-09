package com.platform.shared.qc;

/**
 * Exposes the QC rubric scorer to other contexts without importing the qc context directly.
 * The qc context provides the implementation; ai uses this port.
 */
public interface StructuralQcPort {

    /**
     * Scores the structural quality of an answer text using the QC rubric (§7).
     *
     * @return a normalised score in [0.0, 1.0] (rubric total / 100)
     */
    double score(String answerText, String subjectCode);
}
