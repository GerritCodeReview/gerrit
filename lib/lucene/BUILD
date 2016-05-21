load('//tools/bzl:maven.bzl', 'merge_maven_jars')

# core and backward-codecs both provide
# META-INF/services/org.apache.lucene.codecs.Codec, so they must be merged.
merge_maven_jars(
  name = 'lucene-core-and-backward-codecs',
  srcs = [
    ':backward-codecs_jar-binary_deploy.jar',
    ':lucene-core-binary_deploy.jar',
  ],
  visibility = ['//visibility:public'],
)

java_binary(
  name = 'lucene-core-binary',
  main_class = 'Dummy',
  runtime_deps = [':lucene-core'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'lucene-core',
  exports = ['@lucene_core//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'lucene-analyzers-common',
  exports = ['@lucene_analyzers_common//jar'],
  runtime_deps = [':lucene-core-and-backward-codecs'],
  visibility = ['//visibility:public'],
)

java_binary(
  name = 'backward-codecs_jar-binary',
  main_class = 'Dummy',
  runtime_deps = [':backward-codecs_jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'backward-codecs_jar',
  exports = ['@backward_codecs//jar'],
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
