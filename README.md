# atlas-search-coco
A demo project showing how to build a Java based faceted full text search service using the open source Coco dataset.

To get started run the docker compose file in the root of the project:

```bash
docker compose up java-app
```

This keeps the existing behavior and loads caption text only.

### Run with LM Studio Vector Embeddings
Start LM Studio with an embedding-capable model available on its local API, then run:

```bash
JAVA_APP_ARGS=--lmStudioVectorEmbeddings docker compose up java-app
```

By default the container calls `http://host.docker.internal:1234/v1/embeddings`. Override that if needed:

```bash
LM_STUDIO_BASE_URL=http://host.docker.internal:1234 JAVA_APP_ARGS=--lmStudioVectorEmbeddings docker compose up java-app
```

If you don't pass `JAVA_APP_ARGS=--lmStudioVectorEmbeddings`, the application behaves exactly as before and skips vector embeddings and the vector index.

### Search API Examples
Text search only:

```bash
curl "http://localhost:8222/image/search?text=newspaper&searchType=Text"
```

Vector search only:

```bash
curl "http://localhost:8222/image/search?text=icy%20sculpture%20on%20a%20footpath&searchType=Vector&animal=bear"
```

Hybrid text + vector search:

```bash
curl "http://localhost:8222/image/search?text=newspaper&searchType=Text&searchType=Vector&animal=bird&hasPerson=true"
```

### Run with Tracing
```bash
ATLAS_SEARCH_TRACE_COMMANDS=true mvn compile exec:java
```
