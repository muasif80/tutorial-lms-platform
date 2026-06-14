/**
 * Sync bounded context: the server side of offline-first learning. Implemented in Part 9.
 *
 * <p>The experience layer's hardest distributed problem lives on the client — a learner downloads a course,
 * makes progress offline (possibly on several devices), and reconnects with state to reconcile. This context
 * is where that reconciliation lands: {@link com.scholr.lms.sync.SyncService} merges a
 * {@link com.scholr.lms.sync.domain.SyncBatch} into the authoritative
 * {@link com.scholr.lms.sync.domain.CourseSyncState} using conflict-free rules — a grow-only set (G-Set CRDT)
 * for completed lessons and a last-write-wins register for the position cursor — so the merge is idempotent
 * and order-independent, and a flaky connection can retry safely.
 *
 * <p>Tenant-scoped like every persistence context, with Row-Level Security in the migration.
 */
package com.scholr.lms.sync;
