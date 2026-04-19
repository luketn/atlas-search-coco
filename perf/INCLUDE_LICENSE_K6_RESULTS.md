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
| Average HTTP duration | 15.262 ms | 15.168 ms | -0.094 ms (-0.61%) |
| P95 HTTP duration | 21.057 ms | 21.487 ms | +0.430 ms (+2.04%) |
| Request rate | 1292.29 req/s | 1301.35 req/s | +9.05 req/s (+0.70%) |
| Iterations completed | 12,949 | 13,034 | +85 (+0.66%) |
| Average docs returned | 8.311 | 8.305 | -0.006 (-0.08%) |
| Data received | 67.48 MiB | 81.19 MiB | +13.71 MiB (+20.32%) |
| HTTP failure rate | 0.00% | 0.00% | no change |

## Interpretation

The main effect of `includeLicense=true` in this run was payload size rather than a clear latency or throughput regression.

Observed impact:

- average latency was effectively flat in this sample (`-0.61%`, which is within normal run-to-run noise)
- p95 latency was slightly higher (`+2.04%`)
- throughput was effectively flat to slightly higher (`+0.70%`)
- response payload increased materially (`+20.32%`) because license fields were included

The benchmark stayed error-free in both modes, so the practical tradeoff in this run was richer result documents and larger payloads, with no strong evidence of a meaningful throughput penalty from the implicit lookup path.

## Files

- Baseline summary: `perf/k6-include-license-false.json`
- Baseline log: `perf/k6-include-license-false.log`
- Comparison summary: `perf/k6-include-license-true.json`
- Comparison log: `perf/k6-include-license-true.log`
