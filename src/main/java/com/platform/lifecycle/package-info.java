/**
 * Question Lifecycle context — the only writer of canonical question state. Append-only
 * {@code question_events}, the state machine, version-guarded idempotent transitions.
 * Cross-context effects flow via domain events (outbox + dispatcher).
 */
package com.platform.lifecycle;
