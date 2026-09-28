import http from 'k6/http';
import { check } from 'k6';
import { GATEWAY_URL } from './config.js';
import { token } from './auth.js';

const DEVICE_TYPES = ['SPEAKER', 'CAMERA', 'THERMOSTAT', 'LIGHT', 'LOCK', 'DOORBELL'];

function randomItem(items) {
  return items[Math.floor(Math.random() * items.length)];
}

// `name` groups URLs that differ only by id, so /user/1 and /user/2 are one row in the
// summary and one series in Prometheus instead of thousands.
function request(method, path, name, body, expected) {
  const res = http.request(method, `${GATEWAY_URL}${path}`,
    body === undefined ? null : JSON.stringify(body), {
      headers: {
        Authorization: `Bearer ${token()}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      tags: { name },
      responseCallback: http.expectedStatuses(expected),
    });
  check(res, { [`${name} -> ${expected}`]: (r) => r.status === expected });
  return res;
}

export function createUser() {
  return request('POST', '/api/v1/user', 'POST /user', {
    name: 'Load',
    surname: 'Test',
    // email has a unique key
    email: `load+${Date.now()}-${Math.floor(Math.random() * 1e9)}@example.com`,
    address: 'Rua das Flores, 120 - Sao Paulo/SP',
    alerting: false,
    energyAlertingThreshold: 1000,
  }, 201);
}

export function createDevice(userId) {
  return request('POST', '/api/v1/device', 'POST /device', {
    name: 'Load test device',
    type: randomItem(DEVICE_TYPES),
    location: 'Sala',
    userId,
  }, 201);
}

export const getUser = (id) => request('GET', `/api/v1/user/${id}`, 'GET /user/{id}', undefined, 200);
export const getDevice = (id) => request('GET', `/api/v1/device/${id}`, 'GET /device/{id}', undefined, 200);
export const getDevicesByUser = (userId) =>
  request('GET', `/api/v1/device/user/${userId}`, 'GET /device/user/{userId}', undefined, 200);
export const getUsage = (userId) =>
  request('GET', `/api/v1/usage/${userId}?days=3`, 'GET /usage/{userId}', undefined, 200);
export const getSavingTips = (userId) =>
  request('GET', `/api/v1/insight/saving-tips/${userId}`, 'GET /insight/saving-tips/{userId}', undefined, 200);
export const deleteDevice = (id) => request('DELETE', `/api/v1/device/${id}`, 'DELETE /device/{id}', undefined, 204);
export const deleteUser = (id) => request('DELETE', `/api/v1/user/${id}`, 'DELETE /user/{id}', undefined, 204);

export function sendReading(deviceId) {
  return request('POST', '/api/v1/ingestion', 'POST /ingestion', {
    deviceId,
    energyConsumed: Math.round(Math.random() * 500) / 100,
    timestamp: new Date().toISOString(),
  }, 201);
}
