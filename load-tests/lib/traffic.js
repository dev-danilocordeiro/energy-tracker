import { fail, sleep } from 'k6';
import { DEVICES_PER_USER, INCLUDE_INSIGHT, POOL_SIZE } from './config.js';
import * as api from './api.js';

function randomItem(items) {
  return items[Math.floor(Math.random() * items.length)];
}

// Runs once before the VUs start. The ids it returns are handed to every iteration.
export function createPool() {
  const users = [];
  const devices = [];
  for (let i = 0; i < POOL_SIZE; i++) {
    const user = api.createUser();
    if (user.status !== 201) {
      fail(`setup could not create a user: ${user.status} ${user.body}`);
    }
    const userId = user.json('id');
    users.push(userId);
    for (let j = 0; j < DEVICES_PER_USER; j++) {
      const device = api.createDevice(userId);
      if (device.status !== 201) {
        fail(`setup could not create a device: ${device.status} ${device.body}`);
      }
      devices.push(device.json('id'));
    }
  }
  return { users, devices };
}

// Runs once after the VUs stop, so a run leaves the database as it found it
export function deletePool(pool) {
  pool.devices.forEach((id) => api.deleteDevice(id));
  pool.users.forEach((id) => api.deleteUser(id));
}

// Weighted mix, roughly what the app sees: mostly readings coming in, then reads,
// and a few writes that create and delete their own device.
const ACTIONS = [
  { weight: 35, run: (p) => api.sendReading(randomItem(p.devices)) },
  { weight: 20, run: (p) => api.getUser(randomItem(p.users)) },
  { weight: 20, run: (p) => api.getDevice(randomItem(p.devices)) },
  { weight: 10, run: (p) => api.getDevicesByUser(randomItem(p.users)) },
  { weight: 10, run: (p) => api.getUsage(randomItem(p.users)) },
  {
    weight: 5,
    run: (p) => {
      const created = api.createDevice(randomItem(p.users));
      if (created.status === 201) {
        api.deleteDevice(created.json('id'));
      }
    },
  },
];
if (INCLUDE_INSIGHT) {
  ACTIONS.push({ weight: 1, run: (p) => api.getSavingTips(randomItem(p.users)) });
}
const TOTAL_WEIGHT = ACTIONS.reduce((sum, a) => sum + a.weight, 0);

export function mixedTraffic(pool, thinkTimeSeconds = 1) {
  let pick = Math.random() * TOTAL_WEIGHT;
  const action = ACTIONS.find((a) => (pick -= a.weight) < 0);
  action.run(pool);
  // Think time between requests. Without it a VU is a tight loop, not a user.
  sleep(thinkTimeSeconds * (0.5 + Math.random()));
}
