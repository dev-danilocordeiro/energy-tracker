import http from 'k6/http';
import { fail } from 'k6';
import { CLIENT_ID, CLIENT_SECRET, TOKEN_URL } from './config.js';

// Keycloak tokens live 5 minutes, shorter than most runs, so each VU keeps its own and
// renews it shortly before it expires. Module state is per VU in k6.
const RENEW_BEFORE_MS = 30 * 1000;
let cached = null;

export function fetchToken() {
  if (!CLIENT_SECRET) {
    fail('HET_CLIENT_SECRET is not set (see load-tests/README.md)');
  }
  const res = http.post(TOKEN_URL, {
    grant_type: 'client_credentials',
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
  }, { tags: { name: 'keycloak token' } });
  if (res.status !== 200) {
    fail(`could not get a token from Keycloak: ${res.status} ${res.body}`);
  }
  const body = res.json();
  return { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
}

export function token() {
  if (!cached || Date.now() > cached.expiresAt - RENEW_BEFORE_MS) {
    cached = fetchToken();
  }
  return cached.value;
}
