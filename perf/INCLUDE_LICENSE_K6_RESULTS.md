# K6 Search Benchmark: `includeLicense`

This benchmark compares the `/image/search` endpoint with and without the `includeLicense` query parameter.

In the current implementation:

- `includeLicense=false` keeps `returnStoredSource=true`, so results come from `mongot` stored fields.
- `includeLicense=true` disables `returnStoredSource` and projects `licenseName` / `licenseUrl`, so `mongot` performs the implicit document lookup before returning results.

## Test setup

- App build: branch `codex/vector-search`
- Dataset: existing local COCO dataset already loaded in MongoDB
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
| Average HTTP duration | 19.214 ms | 23.958 ms | +4.744 ms (+24.69%) |
| P95 HTTP duration | 25.690 ms | 31.459 ms | +5.769 ms (+22.46%) |
| Average MongoDB query time | 9.040 ms | 11.093 ms | +2.053 ms (+22.71%) |
| P95 MongoDB query time | 13.418 ms | 16.296 ms | +2.877 ms (+21.44%) |
| Average Java non-Mongo time | 0.638 ms | 1.024 ms | +0.386 ms (+60.57%) |
| P95 Java non-Mongo time | 0.978 ms | 1.352 ms | +0.374 ms (+38.27%) |
| Request rate | 1017.35 req/s | 816.68 req/s | -200.67 req/s (-19.73%) |
| Iterations completed | 10,194 | 8,196 | -1,998 (-19.60%) |
| Average docs returned | 8.312 | 8.332 | +0.020 (+0.24%) |
| Total data received | 56.32 MiB | 53.65 MiB | -2.67 MiB (-4.75%) |
| Data received per request | 5.657 KiB | 6.703 KiB | +1.045 KiB (+18.47%) |
| HTTP failure rate | 0.00% | 0.00% | no change |

## Interpretation

In this run, `includeLicense=true` showed a clear latency and throughput regression relative to the baseline.

Observed impact:

- average latency increased by `24.69%`
- p95 latency increased by `22.46%`
- throughput dropped by `19.73%`
- iterations completed dropped by `19.60%`
- per-request payload increased by `18.47%` because license fields were included
- total data received during the run was lower only because the slower run completed fewer requests

The benchmark stayed error-free in both modes, but this sample does show a meaningful cost from the `includeLicense=true` path. The additional license projection increased payload size per response and coincided with higher end-to-end latency and lower throughput, which is consistent with the extra lookup/projection work happening before results are returned.

## Files

- Baseline summary: `perf/k6-include-license-false.json`
- Baseline log: `perf/k6-include-license-false.log`
- Comparison summary: `perf/k6-include-license-true.json`
- Comparison log: `perf/k6-include-license-true.log`
