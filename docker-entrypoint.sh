#!/bin/sh
set -eu

uid="${GREENWATCH_UID:-1000}"
gid="${GREENWATCH_GID:-1000}"
db_path="${DATABASE_PATH:-/data/greenwatch.db}"

if [ "${db_path#jdbc:}" = "$db_path" ]; then
  db_dir=$(dirname "$db_path")
  mkdir -p "$db_dir"
  if [ "$(id -u)" = "0" ]; then
    chown -R "$uid:$gid" "$db_dir"
  fi
fi

wrapper=""
if [ -x /__cacert_entrypoint ]; then
  wrapper=/__cacert_entrypoint
fi

if [ "$(id -u)" = "0" ] && command -v setpriv >/dev/null 2>&1; then
  if [ -n "$wrapper" ]; then
    exec setpriv --reuid="$uid" --regid="$gid" --clear-groups "$wrapper" "$@"
  fi
  exec setpriv --reuid="$uid" --regid="$gid" --clear-groups "$@"
fi

if [ -n "$wrapper" ]; then
  exec "$wrapper" "$@"
fi
exec "$@"
