package com.platform.shared.code;

public interface CodeVerifierPort {

    /**
     * Judges whether the given code snippet correctly solves the problem described in questionText.
     * Returns 1.0 = correct, 0.0 = incorrect, 0.5 = uncertain or not applicable.
     */
    double verify(String questionText, String codeSnippet);
}
