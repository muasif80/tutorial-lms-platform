/**
 * Real-time bounded context: live classes, chat, and presence — the tier where "real-time" meets
 * scale. Implemented in Part 4.
 *
 * <p>Its load-bearing idea is the {@link com.scholr.lms.realtime.MessageBroker} fan-out seam: the
 * WebSocket gateway tier stays stateless and publishes every message to a broker that fans it out
 * to every node serving a room, so a class scales horizontally instead of collapsing on one node.
 * {@link com.scholr.lms.realtime.LiveClassRoom} adds presence and sequenced messages so a
 * reconnecting client recovers exactly what it missed.
 *
 * <p>The context is transport-agnostic and infrastructure-light by design: an in-memory broker is
 * the default (and what the tests exercise), swappable for Redis pub/sub in production with no
 * change above the port. It is unlike the persistence-heavy contexts — nothing here is a JPA
 * aggregate — but it follows the same boundary discipline.
 */
package com.scholr.lms.realtime;
