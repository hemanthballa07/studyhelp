/**
 * Identity context — accounts, roles (student/expert/admin), sessions, entitlement checks
 * (Spring Authorization Server). Only writer of its canonical tables; cross-context effects
 * flow via domain events (outbox + dispatcher), never by writing another context's tables.
 */
package com.platform.identity;
