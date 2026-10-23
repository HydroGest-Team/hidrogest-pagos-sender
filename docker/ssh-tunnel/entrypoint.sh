#!/bin/sh
# Reenvía hidrogest-tunnel:3306 → 127.0.0.1:3306 de la EC2. Si la conexión cae, ssh termina y
# docker lo reinicia (restart: unless-stopped).
set -eu
: "${SSH_HOST:?falta HIDROGEST_SSH_HOST en .env}"
: "${SSH_USER:?falta HIDROGEST_SSH_USER en .env}"

# La llave llega montada de solo lectura; ssh exige permisos 600 y un dueño válido.
cp /run/secrets/ssh_key /tmp/id_key
chmod 600 /tmp/id_key

exec ssh -i /tmp/id_key -N \
  -o StrictHostKeyChecking=accept-new \
  -o UserKnownHostsFile=/tmp/known_hosts \
  -o ServerAliveInterval=30 -o ServerAliveCountMax=3 \
  -o ExitOnForwardFailure=yes -o ConnectTimeout=15 \
  -L 0.0.0.0:3306:127.0.0.1:"${REMOTE_DB_PORT:-3306}" \
  "${SSH_USER}@${SSH_HOST}"
