/**
 * Cross-cutting plumbing shared by all contexts: event envelope, transactional outbox,
 * pluggable dispatcher, idempotency, telemetry. Any context may depend on {@code shared};
 * {@code shared} depends on no other context.
 */
package com.platform.shared;
