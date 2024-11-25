package com.mycodefu.model;

import java.util.List;

public record ImageSearchResult(List<Image> docs, List<ImageMeta> meta) {
    public record ImageMetaTotal (long total) { }
    public record ImageMetaFacetBucket (String _id, long count) { }
    public record ImageMetaFacet (List<ImageMetaFacetBucket> buckets) { }
    public record ImageMetaFacets (
            List<ImageMetaFacet> accessory,
            List<ImageMetaFacet> animal,
            List<ImageMetaFacet> appliance,
            List<ImageMetaFacet> electronic,
            List<ImageMetaFacet> food,
            List<ImageMetaFacet> furniture,
            List<ImageMetaFacet> indoor,
            List<ImageMetaFacet> kitchen,
            List<ImageMetaFacet> outdoor,
            List<ImageMetaFacet> sports,
            List<ImageMetaFacet> vehicle
    ) { }
    public record ImageMeta (ImageMetaTotal count, ImageMetaFacets facet) { }
}
