/**
 * Payments context — SIMULATED earnings ledger only (never a real payment processor; never
 * real money). Idempotent accrual keyed on a unique {@code source_event_id}. Cross-context
 * effects flow via domain events (outbox + dispatcher).
 */
package com.platform.payments;
