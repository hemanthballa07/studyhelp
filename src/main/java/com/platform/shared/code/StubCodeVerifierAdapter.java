package com.platform.shared.code;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class StubCodeVerifierAdapter implements CodeVerifierPort {

    @Override
    public double verify(String questionText, String codeSnippet) {
        return 0.5;
    }
}
