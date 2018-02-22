#!/bin/sh

set -eux

WAR=$TEST_SRCDIR/gerrit/polygerrit.war
JAVA=$TEST_SRCDIR/local_jdk/bin/java

DB=$(mktemp -d || mktemp -d -t bazel-tmp)
GERRIT_TMP=$(mktemp -d || mktemp -d -t bazel-tmp)

$JAVA -jar ${WAR} \
  init -d ${DB} --batch --dev --no-auto-start

# TODO(viktard): pick unused port, set port into gerrit.config with git-config
PORT=8080

$JAVA -jar ${WAR} \
  daemon -d ${DB} --console-log &

PID=$!
echo Server PID $PID
trap "kill -9 $PID" EXIT

while true ; do
  if curl -s -u admin:secret http://localhost:$PORT/a/accounts/self ; then
    break
  fi
  sleep 1
done

echo "Gerrit is serving"

# Insert some data.
$TEST_SRCDIR/gerrit/contrib/populate-fixture-data.py -u 20 -p $PORT

echo "inserted data"

# Show the result.
curl -s -u admin:secret http://localhost:$PORT/a/projects/

# TODO(viktard): start selenium/webdriver. See
# https://github.com/bazelbuild/rules_webtesting - I'm a fan of Go,
# but Java is mature and well understood in Gerrit.
