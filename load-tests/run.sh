#!/usr/bin/env bash
# Runs a load test scenario with k6 in Docker.
#
#   load-tests/run.sh <smoke|load|stress|spike> [extra k6 args]
#   VUS=50 load-tests/run.sh load
#
# Metrics go to Prometheus (Grafana dashboard "k6 Load Tests"), the live k6 dashboard is
# on http://localhost:5665 while the test runs, and an HTML report lands in
# load-tests/reports/.
set -euo pipefail

cd "$(dirname "$0")/.."

scenario="${1:?usage: load-tests/run.sh <smoke|load|stress|spike> [k6 args]}"
shift
if [[ ! -f "load-tests/${scenario}.js" ]]; then
  echo "unknown scenario: ${scenario}" >&2
  exit 2
fi

testid="${scenario}-$(date +%Y%m%d-%H%M%S)"
mkdir -p load-tests/reports

# Scenario knobs are forwarded only when set, so the script defaults apply otherwise
env_args=()
for var in VUS HOLD PEAK_VUS SPIKE_VUS POOL_SIZE DEVICES_PER_USER INCLUDE_INSIGHT GATEWAY_URL; do
  if [[ -n "${!var:-}" ]]; then
    env_args+=(-e "${var}=${!var}")
  fi
done

# Runs as the current user so the report is written with your ownership
docker compose --profile load-test run --rm \
  --user "$(id -u):$(id -g)" \
  -e K6_WEB_DASHBOARD=true \
  -e K6_WEB_DASHBOARD_EXPORT="reports/${testid}.html" \
  "${env_args[@]}" \
  k6 run --tag testid="${testid}" "$@" "${scenario}.js"
