#!/bin/sh

# This test ensures that new dependencies in nongoogle.bzl go through LC review.

set -eux

bzl=$(pwd)/tools/nongoogle.bzl

TMP=$(mktemp -d || mktemp -d -t /tmp/tmp.XXXXXX)

grep 'name = "[^"]*"' ${bzl} | sed 's|^[^"]*"||g;s|".*$||g' | sort > $TMP/names

cat << EOF > $TMP/want
autolink
byte-buddy
byte-buddy-agent
cglib-3_2
commons-pool
commons-text
diffutils
dropwizard-core
duct-tape
eddsa
elasticsearch-rest-client
flexmark
flexmark-ext-abbreviation
flexmark-ext-anchorlink
flexmark-ext-autolink
flexmark-ext-definition
flexmark-ext-emoji
flexmark-ext-escaped-character
flexmark-ext-footnotes
flexmark-ext-gfm-issues
flexmark-ext-gfm-strikethrough
flexmark-ext-gfm-tables
flexmark-ext-gfm-tasklist
flexmark-ext-gfm-users
flexmark-ext-ins
flexmark-ext-jekyll-front-matter
flexmark-ext-superscript
flexmark-ext-tables
flexmark-ext-toc
flexmark-ext-typographic
flexmark-ext-wikilink
flexmark-ext-yaml-front-matter
flexmark-formatter
flexmark-html-parser
flexmark-profile-pegdown
flexmark-util
guava-failureaccess
hamcrest-core
html-types
httpasyncclient
httpcore-nio
j2objc
jackson-core
javassist
javax-activation
jna
jruby
mina-core
mockito
nekohtml
objenesis
objenesis
openid-consumer
powermock-api-easymock
powermock-api-support
powermock-core
powermock-module-junit4
powermock-module-junit4-common
powermock-reflect
sshd
sshd-mina
testcontainers
testcontainers-elasticsearch
tukaani-xz
visible-assertions
xerces
EOF

diff -u $TMP/names $TMP/want
