// Stress: steps well past the expected load to find where latency and errors take off.
// Crossing a threshold here is the point: it is reported (exit code 99), not a reason to stop.
import { DEFAULT_THRESHOLDS } from './lib/config.js';
import { createPool, deletePool, mixedTraffic } from './lib/traffic.js';

const PEAK = Number(__ENV.PEAK_VUS || 200);

export const options = {
  stages: [
    { duration: '1m', target: Math.round(PEAK * 0.25) },
    { duration: '2m', target: Math.round(PEAK * 0.25) },
    { duration: '1m', target: Math.round(PEAK * 0.5) },
    { duration: '2m', target: Math.round(PEAK * 0.5) },
    { duration: '1m', target: PEAK },
    { duration: '2m', target: PEAK },
    { duration: '1m', target: 0 },
  ],
  thresholds: DEFAULT_THRESHOLDS,
};

export const setup = createPool;
export const teardown = deletePool;

export default function (pool) {
  // Half the think time of the load test: each VU pushes harder
  mixedTraffic(pool, 0.5);
}
