package(default_visibility=['//visibility:public'])

java_library(
  name = 'elasticsearch',
  data = [ '//lib:LICENSE-Apache2.0' ],
  exports = [ '@elasticsearch//jar' ],
  runtime_deps = [
    ':jna',
    ':hppc',
    ':jsr166e',
    ':netty',
    ':t-digest',
    ':compress-lzf',
    '//lib/joda:joda-time',
    '//lib/lucene:lucene-codecs',
    '//lib/lucene:lucene-highlighter',
    '//lib/lucene:lucene-join',
    '//lib/lucene:lucene-memory',
    '//lib/lucene:lucene-sandbox',
    '//lib/lucene:lucene-suggest',
    '//lib/lucene:lucene-queries',
    '//lib/lucene:lucene-spatial',
    '//lib/jackson:jackson-core',
    '//lib/jackson:jackson-dataformat-cbor',
    '//lib/jackson:jackson-dataformat-smile',
  ]
)

# Java REST client for Elasticsearch.
VERSION = '0.1.7'

java_library(
  name = 'jest-common',
  exports = [ '@jest_common//jar' ],
  data = [ '//lib:LICENSE-Apache2.0' ],
)

java_library(
  name = 'jest',
  exports = [ '@jest//jar' ],
  data = [ '//lib:LICENSE-Apache2.0' ],
  runtime_deps = [
    ':elasticsearch',
    ':jest-common',
    '//lib/commons:lang3',
    '//lib/httpcomponents:httpasyncclient',
    '//lib/httpcomponents:httpclient',
    '//lib/httpcomponents:httpcore-nio',
    '//lib/httpcomponents:httpcore-niossl',
  ],
)

java_library(
  name = 'compress-lzf',
  exports = [ '@compress_lzf//jar' ],
  data = [ '//lib:LICENSE-Apache2.0' ],
  visibility = ['//lib/elasticsearch:__pkg__'],
)

java_library(
  name = 'hppc',
  exports = [ '@hppc//jar' ],
  data = [ '//lib:LICENSE-Apache2.0' ],
  visibility = ['//lib/elasticsearch:__pkg__'],
)

java_library(
  name = 'jsr166e',
  exports = [ '@jsr166e//jar' ],
  data = [ '//lib:LICENSE-Apache2.0' ],
  visibility = ['//lib/elasticsearch:__pkg__'],
)

java_library(
  name = 'netty',
  exports = [ '@netty//jar' ],
  data = [ '//lib:LICENSE-Apache2.0' ],
  visibility = ['//lib/elasticsearch:__pkg__'],
)

java_library(
  name = 't-digest',
  exports = [ '@t_digest//jar' ],
  data = [ '//lib:LICENSE-Apache2.0' ],
  visibility = ['//lib/elasticsearch:__pkg__'],
)

java_library(
  name = 'jna',
  exports = [ '@jna//jar' ],
  data = [ '//lib:LICENSE-Apache2.0' ],
)
