# Load tests

[k6](https://grafana.com/docs/k6/latest/) scenarios that drive the API through the
gateway, the same way a client would: Keycloak token, JWT on every request.

## Running

Needs the compose stack (`docker compose up -d`), all services running and
`HET_CLIENT_SECRET` in the root `.env`. k6 itself runs in Docker, nothing to install.

```bash
load-tests/run.sh smoke                  # 2 VUs, 30s: does everything answer?
load-tests/run.sh load                   # ramp to 20 VUs, hold 3m: the baseline
load-tests/run.sh stress                 # steps to 200 VUs: where does it break?
load-tests/run.sh spike                  # 5 -> 150 VUs in 10s and back: does it recover?

VUS=50 HOLD=10m load-tests/run.sh load   # knobs are environment variables
load-tests/run.sh load --vus 5           # extra args go to `k6 run`
```

For a clean baseline, start ingestion-service with `SIMULATION_ENABLED=false`. Its
simulator otherwise posts ~200 readings/s straight to the service, and that traffic
shows up in every service-side number.

| Variable | Default | Used by |
|---|---|---|
| `VUS` / `HOLD` | `20` / `3m` | load |
| `PEAK_VUS` | `200` | stress |
| `SPIKE_VUS` | `150` | spike |
| `POOL_SIZE` / `DEVICES_PER_USER` | `20` / `2` | all: test data created in `setup()` |
| `INCLUDE_INSIGHT` | `false` | all: adds insight-service calls (Ollama, tens of seconds each) |
| `GATEWAY_URL` | `http://localhost:9000` | all |

## Results

- **Grafana → k6 Load Tests** (`/d/energy-tracker-k6`): k6 pushes its metrics to
  Prometheus. Pick a run in *Test run*. The last row shows the services' own
  latency, CPU, heap and connection pool for the same window.
- **Live dashboard** at <http://localhost:5665> while a test runs.
- **HTML report** in `load-tests/reports/<scenario>-<timestamp>.html` (gitignored).
- The terminal summary. The exit code is `99` when a threshold is crossed:
  more than 1% failed requests, p95 above 500ms, or checks below 99%.

## How the scenarios work

- `setup()` creates `POOL_SIZE` users with `DEVICES_PER_USER` devices each.
  `teardown()` deletes them, so a run leaves the database as it found it. A run
  interrupted with Ctrl+C before teardown leaves those rows behind.
- Each iteration picks one weighted action (`lib/traffic.js`): 35% readings to
  ingestion, 20% get user, 20% get device, 10% devices by user, 10% usage, 5% create
  and delete a device. Then it sleeps for about a second of think time (half that in
  stress).
- Requests are tagged with a `name` like `GET /user/{id}`, so every id lands in one
  row of the summary and one Prometheus series.
- Each VU keeps its own Keycloak token and renews it before it expires. Tokens last
  5 minutes, less than most runs.
- k6 uses host networking because the gateway checks the token issuer
  (`localhost:8091`). A token fetched through `host.docker.internal` would be rejected.
