#!/bin/sh
set -e

host="$1"
shift
cmd="$@"

until wget -qS -O- http://$host 2>&1 > /dev/null; do
    >&2 sleep 1
done

>&2 echo "$host is up"

exec $cmd
