package com.mycodefu.mongodb.search;

import java.util.List;

public record VectorQueryPlan(
        List<Double> queryVector,
        long filteredDocumentCount,
        int vectorResultLimit,
        int numCandidates
) {
}
