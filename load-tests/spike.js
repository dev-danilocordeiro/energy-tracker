// Spike: jumps from almost idle to a burst in seconds, then drops back. Shows whether
// the circuit breakers trip and whether the services recover once the burst is gone.
import { DEFAULT_THRESHOLDS } from './lib/config.js';
import { createPool, deletePool, mixedTraffic } from './lib/traffic.js';

const SPIKE = Number(__ENV.SPIKE_VUS || 150);

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '10s', target: SPIKE },
    { duration: '1m', target: SPIKE },
    { duration: '10s', target: 5 },
    { duration: '1m', target: 5 },
    { duration: '10s', target: 0 },
  ],
  thresholds: DEFAULT_THRESHOLDS,
};

export const setup = createPool;
export const teardown = deletePool;

export default function (pool) {
  mixedTraffic(pool);
}
