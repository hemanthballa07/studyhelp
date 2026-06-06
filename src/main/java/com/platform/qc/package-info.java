/**
 * Quality Control context — transparent rule-based rubric scorer. Consumes answer events,
 * emits QcPassed/QcFailed/RevisionRequested. QC never writes question state — lifecycle
 * applies the transition on the QC event.
 */
package com.platform.qc;
