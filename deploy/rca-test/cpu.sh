#!/usr/bin/env bash
# Bounded CPU stress executes INSIDE the selected test JVM container's cgroup.
set -euo pipefail
[[ ${FT_TEST_ENVIRONMENT:-} == true ]] || { echo "Test environment required" >&2; exit 64; }
svc=${1:?booking-service or event-service}; seconds=${2:?seconds}; workers=${3:-2}
[[ $svc == booking-service || $svc == event-service ]]
[[ $seconds =~ ^[0-9]+$ && $workers =~ ^[0-9]+$ ]] &&
  (( seconds > 0 && seconds <= 300 && workers > 0 && workers <= 4 ))
dc=(docker compose --env-file "${FT_TEST_ENV_FILE:-deploy/rca-test/.env.example}" -p ft-bi -f deploy/rca-test/compose.yml)
cid=$("${dc[@]}" ps -q "$svc")
[[ -n $cid && $(docker inspect -f '{{index .Config.Labels "ft.bi.test"}}' "$cid") == true ]]
docker inspect -f 'quotaNanoCpus={{.HostConfig.NanoCpus}} pid={{.State.Pid}}' "$cid"
"${dc[@]}" exec -T "$svc" sh -c 'cat /proc/self/cgroup; cat /sys/fs/cgroup/cpu.max; cat /sys/fs/cgroup/cpu.stat'
# Variables below expand inside the container, not in this host shell.
# shellcheck disable=SC2016
"${dc[@]}" exec -T "$svc" sh -c '
  duration=$1; workers=$2; children=""
  cleanup() { for p in $children; do kill "$p" 2>/dev/null || true; done; }
  trap cleanup EXIT INT TERM
  i=0
  while [ "$i" -lt "$workers" ]; do
    (while :; do :; done) & children="$children $!"
    i=$((i+1))
  done
  sleep "$duration"
' sh "$seconds" "$workers"
"${dc[@]}" exec -T "$svc" sh -c 'cat /sys/fs/cgroup/cpu.stat'
