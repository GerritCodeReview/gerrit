#!/bin/sh

# This test ensures that new dependencies in nongoogle.bzl go through LC review.

set -eux

bzl=$(pwd)/tools/nongoogle.bzl

TMP=$(mktemp -d || mktemp -d -t /tmp/tmp.XXXXXX)

grep 'name = "[^"]*"' ${bzl} | sed 's|^[^"]*"||g;s|".*$||g' | sort > $TMP/names

cat << EOF > $TMP/want
backward-codecs
cglib-3_2
commons-io
docker-java-api
docker-java-transport
dropwizard-core
duct-tape
eddsa
flogger
flogger-log4j-backend
flogger-system-backend
guava
guava-testlib
guice-assistedinject
guice-library
guice-servlet
httpasyncclient
httpcore-nio
j2objc
jackson-annotations
jackson-core
jimfs
jna
lucene-analyzers-common
lucene-core
lucene-misc
lucene-queryparser
mina-core
nekohtml
objenesis
openid-consumer
soy
sshd-mina
sshd-osgi
sshd-sftp
testcontainers
truth
truth-java8-extension
truth-liteproto-extension
truth-proto-extension
tukaani-xz
visible-assertions
xerces
EOF

diff -u $TMP/names $TMP/want
