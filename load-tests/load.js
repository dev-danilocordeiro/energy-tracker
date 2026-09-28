// Load: the expected, steady traffic. The baseline later changes (cache, rate limiting)
// are measured against. VUS scales the plateau.
import { DEFAULT_THRESHOLDS } from './lib/config.js';
import { createPool, deletePool, mixedTraffic } from './lib/traffic.js';

const VUS = Number(__ENV.VUS || 20);

export const options = {
  stages: [
    { duration: '1m', target: VUS },
    { duration: __ENV.HOLD || '3m', target: VUS },
    { duration: '30s', target: 0 },
  ],
  thresholds: DEFAULT_THRESHOLDS,
};

export const setup = createPool;
export const teardown = deletePool;

export default function (pool) {
  mixedTraffic(pool);
}
