// Smoke: a couple of VUs for 30s. Proves the stack answers and the script works before
// running anything heavier.
import { DEFAULT_THRESHOLDS } from './lib/config.js';
import { createPool, deletePool, mixedTraffic } from './lib/traffic.js';

export const options = {
  vus: 2,
  duration: '30s',
  thresholds: DEFAULT_THRESHOLDS,
};

export const setup = createPool;
export const teardown = deletePool;

export default function (pool) {
  mixedTraffic(pool);
}
