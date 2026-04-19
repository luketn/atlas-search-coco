package com.mycodefu.mongodb.atlas;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.ImageSearchResult;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MongoConnectionTracingLogSearch {
    private static final Logger log = LoggerFactory.getLogger(MongoConnectionTracingLogSearch.class);

    public static boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

    public static void trace(String message) {
        log.trace(message);
    }

    public static void trace(List<? extends Bson> aggregateStages) {
        if (isTraceEnabled()) {
            if (MongoConnectionTracingLogSearch.isTraceEnabled()) {
                StringBuilder logs = new StringBuilder();
                logs.append("[").append(System.lineSeparator());
                boolean first = true;
                for (Bson aggregateStage : aggregateStages) {
                    if (first) {
                        first = false;
                    } else {
                        logs.append(",");
                    }
                    String jsonStage = aggregateStage.toBsonDocument().toJson(JsonWriterSettings.builder().indent(true).build());
                    logs.append(jsonStage).append(System.lineSeparator());
                }
                logs.append(System.lineSeparator()).append("]");
                trace(logs.toString());
            }
        }
    }
}
