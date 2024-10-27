package com.mycodefu.datapreparation.source;

public record ImageObject(
        int id,
        String superCategory,
        String category,
        double[] bbox
) {}