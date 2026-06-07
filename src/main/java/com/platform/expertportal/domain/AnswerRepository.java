package com.platform.expertportal.domain;

import java.util.UUID;

/** Persists expert answers. The expert portal owns this table (master-design 4). */
public interface AnswerRepository {

    /** Insert an answer; {@code stale} marks a submission that did not transition the question. */
    void insert(UUID id, UUID questionId, UUID expertId, String body, boolean stale);
}
