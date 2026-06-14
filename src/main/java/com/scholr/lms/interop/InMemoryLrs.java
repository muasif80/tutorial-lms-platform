package com.scholr.lms.interop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.scholr.lms.interop.domain.XapiStatement;
import org.springframework.stereotype.Component;

/**
 * An in-process {@link LearningRecordStore}. Models the two properties a real LRS must honor: a statement is
 * stored once (idempotent on its id, so re-translating an event can't create duplicate records), and reads
 * are filtered to a tenant by hand because the LRS — like the search index in Part 6 — has no Postgres
 * Row-Level Security to fall back on.
 */
@Component
public class InMemoryLrs implements LearningRecordStore {

    /** statementId → statement (idempotent store). */
    private final Map<UUID, XapiStatement> statements = new ConcurrentHashMap<>();

    @Override
    public void record(XapiStatement statement) {
        statements.putIfAbsent(statement.id(), statement);
    }

    @Override
    public List<XapiStatement> statementsFor(UUID tenantId, UUID actorId) {
        List<XapiStatement> out = new ArrayList<>();
        for (XapiStatement s : statements.values()) {
            if (s.tenantId().equals(tenantId) && s.actorId().equals(actorId)) {
                out.add(s);
            }
        }
        return out;
    }
}
