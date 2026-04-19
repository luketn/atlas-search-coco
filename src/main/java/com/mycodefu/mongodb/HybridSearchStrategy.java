package com.mycodefu.mongodb;

import com.mycodefu.model.ImageSearchResult;

final class HybridSearchStrategy implements SearchStrategy {
    @Override
    public ImageSearchResult search(ImageDataAccess dataAccess, SearchRequest request) {
        return dataAccess.executeHybridSearch(request);
    }
}
