#!/usr/bin/env bash

MYDIR=$(dirname $0)
if [ ! -d $MYDIR/app ]
then
  mkdir $MYDIR/app && chmod a+rwx $MYDIR/app
fi

pushd $MYDIR > /dev/null
trap popd EXIT

docker-compose up --abort-on-container-exit --force-recreate
