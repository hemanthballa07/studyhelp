package com.platform.expertportal.app;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an expert tries to claim a subject they are not registered for; surfaces as 403. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class NotEligibleForSubjectException extends RuntimeException {

    public NotEligibleForSubjectException(UUID expertId, String subject) {
        super("expert " + expertId + " is not registered for subject '" + subject + "'");
    }
}
