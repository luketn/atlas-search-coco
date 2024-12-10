package com.mycodefu.model;

import java.util.List;

public record ImageSearchResult(List<Image> docs, List<ImageMeta> meta) {
    public record ImageMeta (ImageMetaTotal count, ImageMetaFacets facet) { }
    public record ImageMetaTotal (long total) { }
    public record ImageMetaFacets (
            ImageMetaFacet accessory,
            ImageMetaFacet animal,
            ImageMetaFacet appliance,
            ImageMetaFacet electronic,
            ImageMetaFacet food,
            ImageMetaFacet furniture,
            ImageMetaFacet indoor,
            ImageMetaFacet kitchen,
            ImageMetaFacet outdoor,
            ImageMetaFacet sports,
            ImageMetaFacet vehicle
    ) { }
    public record ImageMetaFacet (List<ImageMetaFacetBucket> buckets) { }
    public record ImageMetaFacetBucket (String _id, long count) { }
}
