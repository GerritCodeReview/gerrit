#!/bin/sh -e

if [ -f /app/polygerrit_ui.zip ]; then
    echo "PolyGerrit UI already built"
else
    if [ -d /src ]; then
       echo "Building from dev sources"
       cd /src
    else
        echo "Building from master"
        cd && git clone https://gerrit.googlesource.com/gerrit
        cd ~/gerrit
    fi

    if [ "$UPGRADE_SERVER" ]; then
        echo "Building gerrit.war"
        bazel build gerrit
        cp bazel-bin/gerrit.war /app/
    fi

    echo "Building polygerrit_ui.zip"
    export BAZEL_OPTS="--spawn_strategy=standalone --genrule_strategy=standalone"
    bazel build //polygerrit-ui/app:polygerrit_ui
    cp bazel-genfiles/polygerrit-ui/app/polygerrit_ui.zip /app/
fi

echo "PolyGerrit UI build complete"
sleep 3600
