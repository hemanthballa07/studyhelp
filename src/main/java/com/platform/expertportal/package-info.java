/**
 * Expert Portal context — queue view plus atomic claim via Postgres
 * {@code FOR UPDATE SKIP LOCKED}, lease TTL/expiry, late-submission handling.
 * Cross-context effects flow via domain events (outbox + dispatcher).
 */
package com.platform.expertportal;
