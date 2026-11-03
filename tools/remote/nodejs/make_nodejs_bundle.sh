#!/bin/sh
# Bundles node and libatomic.so.1 into one self-extracting executable: the stub
# (nodejs_stub.sh) followed by a tar archive. Needs GNU tar for --transform.
# Usage: make_nodejs_bundle.sh OUT STUB NODE LIBATOMIC
set -eu
out=$1 stub=$2 node=$3 libatomic=$4

cat "$stub" > "$out"
tar -chf - --mode=a+rx \
  --transform="s|^$node\$|node|" \
  --transform="s|^$libatomic\$|libatomic.so.1|" \
  "$node" "$libatomic" >> "$out"
chmod +x "$out"
