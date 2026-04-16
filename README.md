# atlas-search-coco
A demo project showing how to build a Java based faceted full text search service using the open source Coco dataset.

To get started run the docker compose file in the root of the project:

```bash
docker compose up java-app
```

### Run with Tracing
```bash
ATLAS_SEARCH_TRACE_COMMANDS=true mvn compile exec:java
```
