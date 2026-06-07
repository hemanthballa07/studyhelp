package com.platform.expertportal.domain;

import java.util.UUID;

/** The expert profile/subjects notion: which subjects an expert is registered to claim from. */
public interface ExpertSubjectRepository {

    /** Register the expert for a subject; idempotent. */
    void register(UUID expertId, String subject);

    /** Whether the expert is registered for the subject. */
    boolean handles(UUID expertId, String subject);
}
