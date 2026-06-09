package com.platform.shared.math;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Test stub: returns 0.5 (neutral) for all inputs. Active in the test profile only. */
@Service
@Profile("test")
public class StubMathVerifierAdapter implements MathVerifierPort {

    @Override
    public double verify(String questionText, String candidateFinalAnswer) {
        return 0.5;
    }
}
