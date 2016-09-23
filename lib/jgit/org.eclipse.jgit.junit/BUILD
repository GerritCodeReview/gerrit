load('//tools/bzl:unsign.bzl', 'unsign_jars')

java_library(
  name = 'junit-signed',
  exports = ['@jgit_junit//jar'],
  runtime_deps = ['//lib/jgit/org.eclipse.jgit:jgit'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-DO_NOT_DISTRIBUTE'],
)

unsign_jars(
  name = 'junit',
  deps = [':junit-signed'],
    visibility = ['//visibility:public'],
)
