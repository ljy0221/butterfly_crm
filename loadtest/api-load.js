import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '60s',  target: 30 },
    { duration: '180s', target: 30 },
    { duration: '60s',  target: 0 },
    { duration: '120s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'https://k14b101.p.ssafy.io';
const API_PATH = __ENV.API_PATH || '/api/v1/auth/login';

const payload = JSON.stringify({
  email: 'loadtest@butterfly.local',
  password: 'wrong-password-on-purpose',
});

const params = {
  headers: { 'Content-Type': 'application/json' },
  timeout: '10s',
};

export default function () {
  const res = http.post(`${BASE_URL}${API_PATH}`, payload, params);
  check(res, {
    'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
  });
  sleep(1);
}
