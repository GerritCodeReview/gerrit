#!/bin/sh -e

if [ -f /app/polygerrit_ui.zip ]
then
  echo "PolyGerrit UI already built"

else
  echo "Building PolyGerrit UI ..."
  cd && git clone https://gerrit.googlesource.com/gerrit
  cd ~/gerrit
  export BAZEL_OPTS="--spawn_strategy=standalone --genrule_strategy=standalone"
  bazel build //polygerrit-ui/app:polygerrit_ui
  cp bazel-genfiles/polygerrit-ui/app/polygerrit_ui.zip /app/
fi

echo "PolyGerrit UI build complete"
sleep 3600

