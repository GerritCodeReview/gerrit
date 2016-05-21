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
)

java_library(
  name = 'lucene-analyzers-common',
  exports = ['@lucene_analyzers_common//jar'],
  runtime_deps = [':lucene-core-and-backward-codecs'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'lucene-misc',
  exports = ['@lucene_misc//jar'],
  runtime_deps = [':lucene-core-and-backward-codecs'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'lucene-queryparser',
  exports = ['@lucene_queryparser//jar'],
  runtime_deps = [':lucene-core-and-backward-codecs'],
  visibility = ['//visibility:public'],
)
