package com.mycodefu.mongodb.search;

import java.util.List;

public final class VectorQueryPlanner {
    private static final int MIN_VECTOR_CANDIDATES = 100;
    private static final int MAX_VECTOR_CANDIDATES = 10_000;

    private VectorQueryPlanner() {
    }

    public static VectorQueryPlan plan(List<Double> queryVector, long filteredDocumentCount) {
        int desiredVectorResultLimit = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, filteredDocumentCount));
        int numCandidates = Math.min(MAX_VECTOR_CANDIDATES, Math.max(desiredVectorResultLimit, MIN_VECTOR_CANDIDATES));
        int vectorResultLimit = Math.min(desiredVectorResultLimit, numCandidates);
        return new VectorQueryPlan(queryVector, Math.max(1L, filteredDocumentCount), vectorResultLimit, numCandidates);
    }
}
