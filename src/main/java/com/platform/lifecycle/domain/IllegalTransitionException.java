package com.platform.lifecycle.domain;

/**
 * Thrown when a state transition is not permitted by {@link QuestionStateMachine} (master-design
 * section 6.1). This is a programming error in the calling code, distinct from a concurrency loss,
 * and is raised before any database write so an illegal transition mutates nothing.
 */
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(QuestionState from, QuestionState to) {
        super("illegal question transition: " + from + " -> " + to);
    }
}
