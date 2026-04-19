import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8222').replace(/\/$/, '');
const requestTimeout = __ENV.REQUEST_TIMEOUT || '10s';
const includeLicense = (__ENV.INCLUDE_LICENSE || 'false').toLowerCase() === 'true';
const searchFacetNames = [
  'animal',
  'appliance',
  'electronic',
  'food',
  'furniture',
  'indoor',
  'kitchen',
  'outdoor',
  'sports',
  'vehicle',
];

const dataset = new SharedArray('search dataset', () => {
  const jsonl = open('./k6-data.jsonl')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  if (jsonl.length === 0) {
    throw new Error('k6-data.jsonl is empty. Generate it with the K6DataGenerator utility first.');
  }

  return jsonl.map((line) => JSON.parse(line));
});

const successfulChecks = new Rate('search_checks');
const searchRequests = new Counter('search_requests');
const searchDocsReturned = new Trend('search_docs_returned');
const searchMongoTime = new Trend('search_mongodb_time_ms');
const searchJavaTime = new Trend('search_java_non_mongo_time_ms');
const searchHttpTime = new Trend('search_http_time_ms');

export const options = {
  scenarios: {
    search_api: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_VUS || 20),
      duration: __ENV.K6_DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<750', 'p(99)<1500'],
    search_checks: ['rate>0.99'],
  },
};

export function setup() {
  return {
    datasetSize: dataset.length,
  };
}

export default function () {
  const sample = dataset[Math.floor(Math.random() * dataset.length)];
  const url = `${baseUrl}/image/search?${toQueryString(sample)}`;
  const response = http.get(url, {
    headers: {
      Accept: 'application/json',
    },
    tags: {
      endpoint: 'image_search',
      filtered: hasFacetFilters(sample) ? 'true' : 'false',
      include_license: String(includeLicense),
    },
    timeout: requestTimeout,
  });

  searchRequests.add(1);
  searchHttpTime.add(response.timings.duration);

  let body;
  try {
    body = response.json();
  } catch (error) {
    body = null;
  }

  searchDocsReturned.add(body !== null && Array.isArray(body.docs) ? body.docs.length : 0);

  const stats = body !== null && typeof body === 'object' && body.stats !== undefined ? body.stats : null;
  if (stats !== null && typeof stats === 'object' && Number.isFinite(stats.totalTimeMs)) {
    searchMongoTime.add(stats.totalTimeMs);
  }
  if (
    stats !== null &&
    typeof stats === 'object' &&
    Number.isFinite(stats.totalJavaTimeMs) &&
    Number.isFinite(stats.totalTimeMs)
  ) {
    searchJavaTime.add(Math.max(0, stats.totalJavaTimeMs - stats.totalTimeMs));
  }

  const ok = check(response, {
    'status is 200': (res) => res.status === 200,
    'response is json': (res) => (res.headers['Content-Type'] || '').includes('application/json'),
    'docs is an array': () => body !== null && Array.isArray(body.docs),
    'meta is an array': () => body !== null && Array.isArray(body.meta),
    'stats has java timing when present': () =>
      stats === null ||
      typeof stats !== 'object' ||
      !Number.isFinite(stats.totalTimeMs) ||
      Number.isFinite(stats.totalJavaTimeMs),
  });

  successfulChecks.add(ok);
}

function toQueryString(sample) {
  const parts = [];
  append(parts, 'text', sample.text);
  append(parts, 'page', sample.page);
  append(parts, 'hasPerson', sample.hasPerson);
  append(parts, 'includeLicense', includeLicense);

  for (const facetName of searchFacetNames) {
    const values = sample[facetName];
    if (Array.isArray(values) && values.length > 0) {
      append(parts, facetName, values.join(','));
    }
  }

  return parts.join('&');
}

function append(parts, key, value) {
  if (value === undefined || value === null || value === '') {
    return;
  }

  parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
}

function hasFacetFilters(sample) {
  return searchFacetNames.some((facetName) => Array.isArray(sample[facetName]) && sample[facetName].length > 0);
}
