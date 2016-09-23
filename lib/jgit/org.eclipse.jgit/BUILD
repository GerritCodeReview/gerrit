load('//tools/bzl:unsign.bzl', 'unsign_jars')

java_library(
  name = 'jgit-signed',
  exports = ['@jgit//jar'],
  runtime_deps = [':ewah'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-jgit'],
)

java_library(
  name = 'ewah',
  exports = ['@ewah//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

unsign_jars(
  name = 'jgit',
  deps = [':jgit-signed'],
  visibility = ['//visibility:public'],
)
