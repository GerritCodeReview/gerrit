#!/bin/sh

# This test ensures that new dependencies in nongoogle.bzl go through LC review.

set -eux

bzl=$(pwd)/tools/nongoogle.bzl

TMP=$(mktemp -d || mktemp -d -t /tmp/tmp.XXXXXX)

grep 'name = "[^"]*"' ${bzl} | sed 's|^[^"]*"||g;s|".*$||g' | sort > $TMP/names

cat << EOF > $TMP/want
cglib-3_2
dropwizard-core
duct-tape
eddsa
elasticsearch-rest-client
httpasyncclient
httpcore-nio
j2objc
jackson-core
jna
jruby
mina-core
nekohtml
objenesis
openid-consumer
sshd
sshd-common
sshd-mina
testcontainers
testcontainers-elasticsearch
tukaani-xz
visible-assertions
xerces
EOF

diff -u $TMP/names $TMP/want
