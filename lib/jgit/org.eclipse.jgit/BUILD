load('//tools/bzl:unsign.bzl', 'unsign_jars')

alias(
  name = 'jgit-alias',
# Swap these lines to route jgit dependency to development tree
  actual = '@jgit_core//jar',
# actual = '@jgit//org.eclipse.jgit:jgit',
  visibility = ['//visibility:public'],
)

java_library(
  name = 'jgit-signed',
  exports = [':jgit-alias'],
  runtime_deps = [':javaewah'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-jgit'],
)

java_library(
  name = 'javaewah',
  exports = ['@javaewah//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

unsign_jars(
  name = 'jgit',
  deps = [':jgit-signed'],
  visibility = ['//visibility:public'],
)
