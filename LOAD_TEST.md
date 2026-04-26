# Load Testing

This project includes a k6 script for load testing the `/image/search` endpoint.

## Prerequisites

- Install k6.
- Start the Java app locally or point `BASE_URL` at a running instance.
- Ensure `k6-data.jsonl` exists. Generate it with `K6DataGenerator` if it is missing.
- For vector or hybrid tests, start the app with vector embeddings enabled and make sure the vector search index exists.

## Parameters

| Parameter | Default | Description |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8222` | Search service base URL. |
| `K6_VUS` | `20` | Number of virtual users. |
| `K6_DURATION` | `30s` | Test duration. |
| `REQUEST_TIMEOUT` | `10s` | Per-request timeout. |
| `INCLUDE_LICENSE` | `false` | Whether to request license fields. |
| `SEARCH_TYPE` | `text` | Search mode: `text`, `vector`, `both`, `combined`, or `hybrid`. |
| `SEARCH_TYPES` | unset | Alternative comma-separated form, such as `text,vector`. Takes precedence over `SEARCH_TYPE`. |

## Examples

Text search with the default profile:

```bash
k6 run k6.js
```

Vector search against a local vector-enabled app:

```bash
SEARCH_TYPE=vector k6 run k6.js
```

Hybrid text and vector search:

```bash
SEARCH_TYPE=both k6 run k6.js
```

Equivalent hybrid run using the comma-separated parameter:

```bash
SEARCH_TYPES=text,vector k6 run k6.js
```

Short comparison run with fewer users:

```bash
K6_VUS=5 K6_DURATION=15s SEARCH_TYPE=text k6 run k6.js
```

Run against another host and export the summary:

```bash
BASE_URL=https://example-search-service.test \
  K6_VUS=20 \
  K6_DURATION=1m \
  SEARCH_TYPE=both \
  k6 run --summary-export perf/k6-hybrid-summary.json k6.js
```

Compare result payloads with and without license fields:

```bash
K6_DURATION=10s K6_VUS=20 INCLUDE_LICENSE=false \
  k6 run --summary-export perf/k6-include-license-false.json k6.js

K6_DURATION=10s K6_VUS=20 INCLUDE_LICENSE=true \
  k6 run --summary-export perf/k6-include-license-true.json k6.js
```

## Notes

The script tags requests with `endpoint`, `filtered`, `include_license`, and `search_type`, which makes it easier to group metrics in k6 outputs.

Vector and hybrid tests call the search API with `searchType=Vector` or repeated `searchType=Text&searchType=Vector` query parameters. If the app was started without a vector index, those requests will fail.
