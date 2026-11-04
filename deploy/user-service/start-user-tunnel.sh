#!/usr/bin/env bash
set -euo pipefail
: "${USER_TUNNEL_SSH_HOST:?Set SSH host}"
: "${USER_TUNNEL_SSH_USER:?Set SSH user}"
: "${USER_TUNNEL_KEY_FILE:?Set private key file}"
: "${USER_TUNNEL_KNOWN_HOSTS_FILE:?Set pinned known_hosts file}"
[[ "$USER_TUNNEL_SSH_HOST" =~ ^[A-Za-z0-9.-]+$ ]]
[[ "$USER_TUNNEL_SSH_USER" =~ ^[A-Za-z0-9_-]+$ ]]
for value in "${USER_TUNNEL_SSH_PORT:-22}" "${USER_MONGO_TUNNEL_PORT:-17017}" \
  "${USER_CONFIG_TUNNEL_PORT:-18888}" "${USER_MONGO_REMOTE_PORT:-27017}" "${USER_CONFIG_REMOTE_PORT:-8888}"; do
  [[ "$value" =~ ^[0-9]+$ ]] && (( value >= 1 && value <= 65535 ))
done
test -f "$USER_TUNNEL_KEY_FILE" && test ! -L "$USER_TUNNEL_KEY_FILE"
[[ "$(stat -c %a "$USER_TUNNEL_KEY_FILE")" == 600 ]]
exec /usr/bin/ssh -NT -i "$USER_TUNNEL_KEY_FILE" -p "${USER_TUNNEL_SSH_PORT:-22}" \
  -o BatchMode=yes -o StrictHostKeyChecking=yes -o UpdateHostKeys=no \
  -o "UserKnownHostsFile=$USER_TUNNEL_KNOWN_HOSTS_FILE" -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=15 -o ServerAliveCountMax=2 \
  -L "127.0.0.1:${USER_MONGO_TUNNEL_PORT:-17017}:127.0.0.1:${USER_MONGO_REMOTE_PORT:-27017}" \
  -L "127.0.0.1:${USER_CONFIG_TUNNEL_PORT:-18888}:127.0.0.1:${USER_CONFIG_REMOTE_PORT:-8888}" \
  "$USER_TUNNEL_SSH_USER@$USER_TUNNEL_SSH_HOST"
