package(default_visibility = [ "//visibility:public"])
load('//tools/bzl:maven.bzl', 'merge_maven_jars')

# core and backward-codecs both provide
# META-INF/services/org.apache.lucene.codecs.Codec, so they must be merged.
merge_maven_jars(
  name = 'lucene-core-and-backward-codecs',
  srcs = [
    '@backward_codecs//jar',
    '@lucene_core//jar',
  ],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-analyzers-common',
  exports = ['@lucene_analyzers_common//jar'],
  runtime_deps = [':lucene-core-and-backward-codecs'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-codecs',
  exports = ['@lucene_codecs//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-core',
  exports = ['@lucene_core//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-misc',
  exports = ['@lucene_misc//jar'],
  runtime_deps = [':lucene-core-and-backward-codecs'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-queryparser',
  exports = ['@lucene_queryparser//jar'],
  runtime_deps = [':lucene-core-and-backward-codecs'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-highlighter',
  exports = [ '@lucene_highlighter//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-join',
  exports = [ '@lucene_join//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-memory',
  exports = [ '@lucene_memory//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-sandbox',
  exports = [ '@lucene_sandbox//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-spatial',
  exports = [ '@lucene_spatial//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-suggest',
  exports = [ '@lucene_suggest//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'lucene-queries',
  exports = [ '@lucene_queries//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)
