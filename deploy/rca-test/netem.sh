#!/usr/bin/env bash
# Run on TEST host: gateway|payment booking-service|event-service delay-ms seconds.
set -euo pipefail
[[ ${FT_TEST_ENVIRONMENT:-} == true ]] || { echo "Test environment required" >&2; exit 64; }
caller=${1:?caller}; target=${2:?target}; delay=${3:?delay-ms}; duration=${4:?seconds}
case "$caller/$target" in
  gateway/booking-service|gateway/event-service) source=api-gateway ;;
  payment/booking-service) source=payment-service ;;
  *) echo "Undefined source/destination pair" >&2; exit 64 ;;
esac
[[ $delay =~ ^[0-9]+$ && $duration =~ ^[0-9]+$ ]] && (( delay <= 30000 && duration > 0 && duration <= 300 ))
dc=(docker compose --env-file "${FT_TEST_ENV_FILE:-deploy/rca-test/.env.example}" -p ft-bi --profile netem -f deploy/rca-test/compose.yml)
cid=$("${dc[@]}" ps -q "$source"); tid=$("${dc[@]}" ps -q "$target")
[[ -n $cid && -n $tid ]]
for id in "$cid" "$tid"; do
  [[ $(docker inspect -f '{{index .Config.Labels "ft.bi.test"}}' "$id") == true ]]
  [[ $(docker inspect -f '{{.HostConfig.NetworkMode}}' "$id") != host ]]
done
ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$tid")
[[ $ip =~ ^[0-9.]+$ ]]
sidecar="netem-$caller"
existing=$("${dc[@]}" exec -T "$sidecar" tc qdisc show dev eth0)
[[ $existing == *noqueue* ]] || { echo "Existing qdisc; no changes made" >&2; exit 64; }
cleanup() { "${dc[@]}" exec -T "$sidecar" tc qdisc del dev eth0 root || true; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"${dc[@]}" exec -T "$sidecar" tc qdisc add dev eth0 root handle 1: prio bands 3 \
  priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
"${dc[@]}" exec -T "$sidecar" tc qdisc add dev eth0 parent 1:3 handle 30: netem delay "${delay}ms"
port=8082; [[ $target != event-service ]] || port=8086
"${dc[@]}" exec -T "$sidecar" tc filter add dev eth0 protocol ip parent 1:0 prio 1 u32 \
  match ip dst "$ip/32" match ip protocol 6 0xff match ip dport "$port" 0xffff flowid 1:3
"${dc[@]}" exec -T "$sidecar" tc -s filter show dev eth0
sleep "$duration"
"${dc[@]}" exec -T "$sidecar" tc -s qdisc show dev eth0
