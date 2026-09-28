// Everything a run can tweak comes from the environment, with defaults for the local stack.
export const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:9000';
export const TOKEN_URL = __ENV.TOKEN_URL
  || 'http://localhost:8091/realms/het-security-realm/protocol/openid-connect/token';
export const CLIENT_ID = __ENV.CLIENT_ID || 'home-energy-tracker-client';
export const CLIENT_SECRET = __ENV.HET_CLIENT_SECRET;

// Users (each with DEVICES_PER_USER devices) created in setup() and shared by every VU
export const POOL_SIZE = Number(__ENV.POOL_SIZE || 20);
export const DEVICES_PER_USER = Number(__ENV.DEVICES_PER_USER || 2);

// insight-service calls Ollama and takes tens of seconds, which would dominate every
// latency number. Off unless asked for.
export const INCLUDE_INSIGHT = __ENV.INCLUDE_INSIGHT === 'true';

// The thresholds every scenario shares. A run fails (exit code 99) when one is crossed.
export const DEFAULT_THRESHOLDS = {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{expected_response:true}': ['p(95)<500'],
  checks: ['rate>0.99'],
};
