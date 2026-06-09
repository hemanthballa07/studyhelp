package com.platform.shared.math;

/**
 * Verifies mathematical correctness of a candidate final answer using a SymPy sidecar (§10.4).
 * Returns a score in [0.0, 1.0]: 1.0 = verified correct, 0.0 = verified wrong,
 * 0.5 = not applicable or error (neutral).
 */
public interface MathVerifierPort {

    double verify(String questionText, String candidateFinalAnswer);
}
