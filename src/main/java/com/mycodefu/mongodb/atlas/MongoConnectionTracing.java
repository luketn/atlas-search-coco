package com.mycodefu.mongodb.atlas;

import com.mongodb.ServerCursor;
import com.mongodb.client.MongoCursor;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import com.mycodefu.model.QueryStats;
import com.mycodefu.mongodb.ImageDataAccess;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public final class MongoConnectionTracing {
    public static final String TRACE_COMMANDS_PROPERTY = "atlas.search.trace.commands";

    private static final String TRACE_COMMANDS_ENV = "ATLAS_SEARCH_TRACE_COMMANDS";
    private static final String TRACE_COMMENT_PREFIX = "atlas-search-trace:";
    private static final Duration TRACE_RETENTION = Duration.ofMinutes(1);
    private static final ConcurrentLinkedDeque<RecordedCommandEvent> recordedCommandEvents = new ConcurrentLinkedDeque<>();
    private static final Map<MongoCursor<?>, String> cursorTraceIds = Collections.synchronizedMap(new WeakHashMap<>());

    private MongoConnectionTracing() {
    }

    public static CommandListener commandListener() {
        return new CommandListener() {
            @Override
            public void commandStarted(CommandStartedEvent event) {
                recordCommandEvent(event);
                CommandListener.super.commandStarted(event);
            }

            @Override
            public void commandSucceeded(CommandSucceededEvent event) {
                recordCommandEvent(event);
                CommandListener.super.commandSucceeded(event);
            }

            @Override
            public void commandFailed(CommandFailedEvent event) {
                recordCommandEvent(event);
                CommandListener.super.commandFailed(event);
            }
        };
    }

    public static boolean isTracingEnabled() {
        String configuredValue = System.getProperty(TRACE_COMMANDS_PROPERTY);
        if (configuredValue == null) {
            configuredValue = System.getenv(TRACE_COMMANDS_ENV);
        }
        return Boolean.parseBoolean(configuredValue);
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString();
    }

    public static String toTraceComment(String traceId) {
        return TRACE_COMMENT_PREFIX + traceId;
    }

    public static void registerCursorTrace(MongoCursor<?> cursor, String traceId) {
        if (cursor == null || traceId == null) {
            return;
        }
        cursorTraceIds.put(cursor, traceId);
    }

    public static List<CommandTrace> getOperationsForCursor(MongoCursor<?> cursor) {
        if (!isTracingEnabled()) {
            return List.of();
        }

        pruneRecordedCommandEvents(Instant.now());

        String traceId = getTraceIdForCursor(cursor);
        Long cursorId = getCursorId(cursor);

        if (traceId == null && cursorId == null) {
            return List.of();
        }

        return buildCommandTraces(snapshotRecordedEvents()).stream()
                .filter(trace -> traceId != null
                        ? traceId.equals(trace.traceId())
                        : cursorId != null && cursorId.equals(trace.cursorId()))
                .toList();
    }

    public static QueryStats getQueryStats(MongoCursor<?> cursor) {
        List<CommandTrace> traces = getOperationsForCursor(cursor);
        if (traces.isEmpty()) {
            return new QueryStats(getTraceIdForCursor(cursor), List.of(), 0.0);
        }

        List<QueryStats.QueryOperationStats> operations = traces.stream()
                .map(trace -> new QueryStats.QueryOperationStats(
                        trace.operationId(),
                        trace.requestId(),
                        trace.commandName(),
                        trace.status(),
                        trace.cursorId(),
                        trace.durationMs()
                ))
                .toList();

        double totalTimeMs = traces.stream()
                .mapToDouble(CommandTrace::durationMs)
                .sum();

        return new QueryStats(traces.getFirst().traceId(), operations, totalTimeMs);
    }

    public static List<CommandTrace> runWithTraces(Runnable runnable) {
        Instant startedAt = Instant.now();
        runnable.run();
        Instant finishedAt = Instant.now();

        if (!isTracingEnabled()) {
            return List.of();
        }

        pruneRecordedCommandEvents(finishedAt);
        return buildCommandTraces(snapshotRecordedEvents()).stream()
                .filter(trace -> !trace.startedAt().isBefore(startedAt) && !trace.finishedAt().isAfter(finishedAt))
                .toList();
    }

    private static void recordCommandEvent(CommandStartedEvent event) {
        if (!isTracingEnabled()) {
            return;
        }
        storeRecordedCommandEvent(new RecordedCommandEvent(
                Instant.now(),
                CommandTraceStatus.STARTED,
                event.getConnectionDescription().getConnectionId().toString(),
                event.getCommandName(),
                event.getOperationId(),
                event.getRequestId(),
                extractCursorIdFromCommand(event.getCommandName(), event.getCommand()),
                extractTraceId(event.getCommand()),
                0.0,
                null
        ));
    }

    private static void recordCommandEvent(CommandSucceededEvent event) {
        if (!isTracingEnabled()) {
            return;
        }
        storeRecordedCommandEvent(new RecordedCommandEvent(
                Instant.now(),
                CommandTraceStatus.SUCCEEDED,
                event.getConnectionDescription().getConnectionId().toString(),
                event.getCommandName(),
                event.getOperationId(),
                event.getRequestId(),
                extractCursorIdFromResponse(event.getResponse()),
                null,
                event.getElapsedTime(TimeUnit.NANOSECONDS) / 1_000_000.0,
                null
        ));
    }

    private static void recordCommandEvent(CommandFailedEvent event) {
        if (!isTracingEnabled()) {
            return;
        }
        storeRecordedCommandEvent(new RecordedCommandEvent(
                Instant.now(),
                CommandTraceStatus.FAILED,
                event.getConnectionDescription().getConnectionId().toString(),
                event.getCommandName(),
                event.getOperationId(),
                event.getRequestId(),
                null,
                null,
                event.getElapsedTime(TimeUnit.NANOSECONDS) / 1_000_000.0,
                event.getThrowable() == null ? null : event.getThrowable().getMessage()
        ));
    }

    private static void storeRecordedCommandEvent(RecordedCommandEvent event) {
        recordedCommandEvents.addLast(event);
        pruneRecordedCommandEvents(event.recordedAt());
    }

    private static void pruneRecordedCommandEvents(Instant now) {
        Instant cutoff = now.minus(TRACE_RETENTION);
        while (true) {
            RecordedCommandEvent oldest = recordedCommandEvents.peekFirst();
            if (oldest == null || !oldest.recordedAt().isBefore(cutoff)) {
                return;
            }
            recordedCommandEvents.pollFirst();
        }
    }

    private static List<RecordedCommandEvent> snapshotRecordedEvents() {
        return List.copyOf(recordedCommandEvents);
    }

    private static List<CommandTrace> buildCommandTraces(List<RecordedCommandEvent> events) {
        Map<CommandKey, RecordedCommandEvent> startedEvents = new HashMap<>();
        Map<Long, String> cursorIdsToTraceIds = new HashMap<>();
        List<CommandTrace> traces = new java.util.ArrayList<>();

        for (RecordedCommandEvent event : events) {
            CommandKey key = new CommandKey(event.connectionId(), event.commandName(), event.operationId(), event.requestId());
            if (event.status() == CommandTraceStatus.STARTED) {
                startedEvents.put(key, event);
                continue;
            }

            RecordedCommandEvent startedEvent = startedEvents.remove(key);
            if (startedEvent == null) {
                continue;
            }

            Long cursorId = event.cursorId() != null ? event.cursorId() : startedEvent.cursorId();
            String traceId = startedEvent.traceId();
            if (traceId == null && cursorId != null) {
                traceId = cursorIdsToTraceIds.get(cursorId);
            }

            CommandTrace trace = new CommandTrace(
                    traceId,
                    event.connectionId(),
                    event.commandName(),
                    event.operationId(),
                    event.requestId(),
                    event.status().name(),
                    cursorId,
                    event.durationMs(),
                    startedEvent.recordedAt(),
                    event.recordedAt(),
                    event.failureMessage()
            );
            traces.add(trace);

            if (trace.traceId() != null && trace.cursorId() != null && trace.cursorId() != 0L) {
                cursorIdsToTraceIds.put(trace.cursorId(), trace.traceId());
            }
        }

        return List.copyOf(traces);
    }

    private static String getTraceIdForCursor(MongoCursor<?> cursor) {
        if (cursor == null) {
            return null;
        }
        return cursorTraceIds.get(cursor);
    }

    private static Long getCursorId(MongoCursor<?> cursor) {
        if (cursor == null) {
            return null;
        }
        ServerCursor serverCursor = cursor.getServerCursor();
        if (serverCursor == null) {
            return null;
        }
        return serverCursor.getId();
    }

    private static Long extractCursorIdFromCommand(String commandName, BsonDocument command) {
        if (!"getMore".equals(commandName) || command == null || !command.containsKey("getMore")) {
            return null;
        }
        return asLong(command.get("getMore"));
    }

    private static Long extractCursorIdFromResponse(BsonDocument response) {
        if (response == null || !response.containsKey("cursor")) {
            return null;
        }
        BsonDocument cursor = response.getDocument("cursor", null);
        if (cursor == null || !cursor.containsKey("id")) {
            return null;
        }
        return asLong(cursor.get("id"));
    }

    private static String extractTraceId(BsonDocument command) {
        if (command == null || !command.containsKey("comment")) {
            return null;
        }

        String comment = command.getString("comment", null) == null ? null : command.getString("comment").getValue();
        if (comment == null || !comment.startsWith(TRACE_COMMENT_PREFIX)) {
            return null;
        }
        return comment.substring(TRACE_COMMENT_PREFIX.length());
    }

    private static Long asLong(BsonValue value) {
        if (value == null || !value.isNumber()) {
            return null;
        }
        return value.asNumber().longValue();
    }

    public record CommandTrace(
            String traceId,
            String connectionId,
            String commandName,
            long operationId,
            int requestId,
            String status,
            Long cursorId,
            double durationMs,
            Instant startedAt,
            Instant finishedAt,
            String failureMessage
    ) { }

    private record CommandKey(String connectionId, String commandName, long operationId, int requestId) { }

    private record RecordedCommandEvent(
            Instant recordedAt,
            CommandTraceStatus status,
            String connectionId,
            String commandName,
            long operationId,
            int requestId,
            Long cursorId,
            String traceId,
            double durationMs,
            String failureMessage
    ) { }

    private enum CommandTraceStatus {
        STARTED,
        SUCCEEDED,
        FAILED
    }

}
