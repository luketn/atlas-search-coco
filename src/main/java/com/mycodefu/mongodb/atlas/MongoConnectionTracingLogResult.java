package com.mycodefu.mongodb.atlas;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.ImageSearchResult;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MongoConnectionTracingLogResult {
    private static final Logger log = LoggerFactory.getLogger(MongoConnectionTracingLogResult.class);

    public static boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

    public static void trace(String message) {
        log.trace(message);
    }

    public static void trace(ImageSearchResult imageSearchResult) {
        if (isTraceEnabled()) {
            trace(JsonUtil.writeToString(imageSearchResult));
        }
    }
}
