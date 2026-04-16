package com.mycodefu.model;

import java.util.List;

public record QueryStats(String traceId, List<QueryOperationStats> operations, double totalTimeMs, Double totalJavaTimeMs) {
    public QueryStats(String traceId, List<QueryOperationStats> operations, double totalTimeMs) {
        this(traceId, operations, totalTimeMs, null);
    }

    public QueryStats withTotalJavaTimeMs(double totalJavaTimeMs) {
        return new QueryStats(traceId, operations, totalTimeMs, totalJavaTimeMs);
    }

    public record QueryOperationStats(
            long operationId,
            int requestId,
            String commandName,
            String status,
            Long cursorId,
            double timeMs
    ) { }
}
