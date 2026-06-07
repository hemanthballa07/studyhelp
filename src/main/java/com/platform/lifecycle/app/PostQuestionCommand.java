package com.platform.lifecycle.app;

import java.time.Instant;
import java.util.UUID;

/** Input to {@link QuestionPostingService#post}: everything needed to create a POSTED question. */
public record PostQuestionCommand(
        UUID studentId, String subject, String title, String body, Instant deadlineAt) {
}
