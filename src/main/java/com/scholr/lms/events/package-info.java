/**
 * Events bounded context: the reliable event backbone every analytics feature is downstream of.
 * Implemented in Part 5.
 *
 * <p>Its load-bearing idea is the <strong>transactional outbox</strong>: business services write an
 * {@link com.scholr.lms.events.domain.OutboxEvent} in the <em>same</em> transaction as their state
 * change (via {@link com.scholr.lms.events.OutboxWriter}), closing the dual-write gap. An
 * {@link com.scholr.lms.events.OutboxRelay} then ships unpublished events to the broker
 * ({@link com.scholr.lms.events.EventPublisher}, backed by Kafka in production) at least once, and
 * idempotent consumers like {@link com.scholr.lms.events.ProgressProjection} turn at-least-once
 * delivery into exactly-once effects using a {@link com.scholr.lms.events.domain.ProcessedEvent}
 * dedup record. The {@link com.scholr.lms.events.domain.LearnerProgress} read model is the payoff —
 * a projection rebuilt from the stream, eventually consistent and never the source of truth.
 *
 * <p>Tenant-scoped like every context; it depends only on the shared kernel and references aggregates
 * by id through self-contained JSON payloads, never by cross-module association.
 */
package com.scholr.lms.events;
