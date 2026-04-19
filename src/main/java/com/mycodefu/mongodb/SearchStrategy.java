package com.mycodefu.mongodb;

import com.mycodefu.model.ImageSearchResult;

interface SearchStrategy {
    ImageSearchResult search(ImageDataAccess dataAccess, SearchRequest request);
}
