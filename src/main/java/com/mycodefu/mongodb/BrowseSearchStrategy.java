package com.mycodefu.mongodb;

import com.mycodefu.model.ImageSearchResult;

final class BrowseSearchStrategy implements SearchStrategy {
    @Override
    public ImageSearchResult search(ImageDataAccess dataAccess, SearchRequest request) {
        return dataAccess.executeFacetSearch(dataAccess.buildBrowseSearchOperator(request), request);
    }
}
