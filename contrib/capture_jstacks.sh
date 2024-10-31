#!/bin/bash -e

Usage() {
    cat <<EOF

  $MYNAME <gerrit_site> [count]

  This program only needs to be pointed to the top level directory of a
  Gerrit site. By default it will capture 2 jstacks of the running Gerrit
  process for the site. A count can optionally be specififed. Captured
  jstacks will be stored under a timestamped directory under
  \$site_dir/jstacks.
EOF

    exit
}

is_gerrit_site_valid() {
    G_CONF=$GERRIT_SITE/etc/gerrit.config
    G_PID=$GERRIT_SITE/logs/gerrit.pid
    if [ -f "$G_CONF" ] && [ -f "$G_PID" ] ; then
        return
    fi
    echo "INVALID SITE: $GERRIT_SITE" >&2
    return 1
}

MYPROG=$(readlink -f -- "$BASH_SOURCE")
MYNAME=$(basename -- "$MYPROG")

GERRIT_SITE=$1 ; shift || Usage ; is_gerrit_site_valid || Usage
COUNT=$1 ; shift || true ; [ -z "$COUNT" ] && COUNT=2

PID=$(< "$G_PID")
JAVA_HOME=$(git config -f "$G_CONF" container.javaHome)

G_STACKS=$GERRIT_SITE/jstacks/$(date -Iminutes)
mkdir -p -- "$G_STACKS"

echo "Grabbing jstacks to $G_STACKS"
for i in $(seq 1 $COUNT) ; do
    echo "$JAVA_HOME"/bin/jstack "$PID" '>' "$G_STACKS"/jstack.$i
    "$JAVA_HOME"/bin/jstack "$PID" > "$G_STACKS"/jstack.$i
    sleep 1
done

