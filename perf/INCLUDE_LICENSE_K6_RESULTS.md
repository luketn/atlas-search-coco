# K6 Search Benchmark: `includeLicense`

This benchmark compares the `/image/search` endpoint with and without the `includeLicense` query parameter.

## Test setup

- App build: branch `codex/vector-search`
- Dataset: local COCO load via `com.mycodefu.Main --loadData`
- Search mode exercised by `k6.js`: text search requests from `k6-data.jsonl`
- K6 profile: `20` VUs for `10s`
- Runs:
  - Baseline: `INCLUDE_LICENSE=false`
  - Comparison: `INCLUDE_LICENSE=true`

Commands used:

```bash
K6_WEB_DASHBOARD=false K6_DURATION=10s K6_VUS=20 \
  k6 run --summary-export perf/k6-include-license-false.json k6.js

K6_WEB_DASHBOARD=false K6_DURATION=10s K6_VUS=20 INCLUDE_LICENSE=true \
  k6 run --summary-export perf/k6-include-license-true.json k6.js
```

## Results

| Metric | `includeLicense=false` | `includeLicense=true` | Difference |
| --- | ---: | ---: | ---: |
| Average HTTP duration | 13.687 ms | 14.361 ms | +0.674 ms (+4.92%) |
| P95 HTTP duration | 18.842 ms | 19.521 ms | +0.679 ms (+3.61%) |
| Request rate | 1441.58 req/s | 1375.70 req/s | -65.89 req/s (-4.57%) |
| Iterations completed | 14,435 | 13,772 | -663 (-4.59%) |
| Average docs returned | 8.329 | 8.282 | -0.047 (-0.56%) |
| Data received | 75.43 MiB | 85.73 MiB | +10.30 MiB (+13.66%) |
| HTTP failure rate | 0.00% | 0.00% | no change |

## Interpretation

The default stored-source path is faster because the search results are served directly from `mongot` without hydrating license fields from `mongod`.

Turning on `includeLicense=true` adds a measurable but modest penalty:

- around `4.9%` slower average latency
- around `3.6%` slower P95 latency
- around `4.6%` lower throughput
- around `13.7%` more response payload received by the client

The benchmark stayed error-free in both modes, so the tradeoff here is performance versus richer result documents, not correctness.

## Files

- Baseline summary: `perf/k6-include-license-false.json`
- Baseline log: `perf/k6-include-license-false.log`
- Comparison summary: `perf/k6-include-license-true.json`
- Comparison log: `perf/k6-include-license-true.log`
