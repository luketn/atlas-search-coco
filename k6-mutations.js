import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8222').replace(/\/$/, '');
const requestTimeout = __ENV.REQUEST_TIMEOUT || '10s';
const mutationMode = (__ENV.K6_MUTATION_MODE || 'paired').toLowerCase();
const mutationPrefix = __ENV.K6_MUTATION_PREFIX || `codex-mutation-${Date.now()}`;
const insertProbability = Number(__ENV.K6_INSERT_PROBABILITY || 0.55);
const syntheticEmbedding = Array.from({ length: 768 }, (_, index) => (index === 0 ? 1.0 : 0.0));

const mutationChecks = new Rate('mutation_checks');
const mutationRequests = new Counter('mutation_requests');
const insertRequests = new Counter('insert_requests');
const deleteRequests = new Counter('delete_requests');
const mutationHttpTime = new Trend('mutation_http_time_ms');
const insertedIdTrend = new Trend('inserted_image_id');

const pendingDeletes = [];

export const options = {
  scenarios: {
    mutation_api: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_VUS || 10),
      duration: __ENV.K6_DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500', 'p(99)<3000'],
    mutation_checks: ['rate>0.95'],
  },
};

export function setup() {
  return {
    prefix: mutationPrefix,
    mode: mutationMode,
  };
}

export default function () {
  if (mutationMode === 'paired') {
    const id = insertSyntheticImage();
    if (id !== null) {
      sleep(Math.random() * 0.08);
      deleteSyntheticImage(id);
    }
    return;
  }

  if (pendingDeletes.length === 0 || Math.random() < insertProbability) {
    const id = insertSyntheticImage();
    if (id !== null) {
      pendingDeletes.push(id);
    }
    return;
  }

  const deleteIndex = Math.floor(Math.random() * pendingDeletes.length);
  const [id] = pendingDeletes.splice(deleteIndex, 1);
  deleteSyntheticImage(id);
}

function insertSyntheticImage() {
  const token = `${mutationPrefix}-vu${__VU}-iter${__ITER}-${Date.now()}`;
  const payload = {
    imageUrl: `https://example.invalid/${encodeURIComponent(token)}.jpg`,
    width: 640,
    height: 480,
    captionResult: {
      caption: `${token} synthetic trace validation image with bicycle apple`,
      captionEmbedding: syntheticEmbedding,
      captionEmbeddingModel: 'codex-static-768',
      visionModel: 'codex-k6-mutation',
      hasPerson: false,
      labels: ['bicycle', 'apple'],
      typedLabels: [
        { type: 'vehicle', label: 'bicycle' },
        { type: 'food', label: 'apple' },
      ],
      categoryLabels: {
        vehicle: ['bicycle'],
        food: ['apple'],
      },
    },
  };

  const response = http.post(`${baseUrl}/image/add`, JSON.stringify(payload), {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    tags: {
      endpoint: 'image_add',
      mutation: 'insert',
      mutation_mode: mutationMode,
    },
    timeout: requestTimeout,
  });

  recordRequest(response, insertRequests);

  let body = null;
  try {
    body = response.json();
  } catch (error) {
    body = null;
  }

  const id = body !== null && Number.isFinite(body._id) ? body._id : null;
  if (id !== null) {
    insertedIdTrend.add(id);
  }

  const ok = check(response, {
    'insert status is 200': (res) => res.status === 200,
    'insert response has id': () => id !== null,
  });
  mutationChecks.add(ok);

  return id;
}

function deleteSyntheticImage(id) {
  const response = http.del(`${baseUrl}/image/delete?id=${encodeURIComponent(String(id))}`, null, {
    headers: {
      Accept: 'application/json',
    },
    tags: {
      endpoint: 'image_delete',
      mutation: 'delete',
      mutation_mode: mutationMode,
    },
    timeout: requestTimeout,
  });

  recordRequest(response, deleteRequests);

  let body = null;
  try {
    body = response.json();
  } catch (error) {
    body = null;
  }

  const ok = check(response, {
    'delete status is 200': (res) => res.status === 200,
    'delete response is acknowledged': () => body !== null && body.deleted === true,
  });
  mutationChecks.add(ok);
}

function recordRequest(response, counter) {
  counter.add(1);
  mutationRequests.add(1);
  mutationHttpTime.add(response.timings.duration);
}
