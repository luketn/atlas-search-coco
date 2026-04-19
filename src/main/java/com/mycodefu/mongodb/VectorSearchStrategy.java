package com.mycodefu.mongodb;

import com.mycodefu.model.ImageSearchResult;

final class VectorSearchStrategy implements SearchStrategy {
    @Override
    public ImageSearchResult search(ImageDataAccess dataAccess, SearchRequest request) {
        return dataAccess.executeVectorSearch(request);
    }
}
