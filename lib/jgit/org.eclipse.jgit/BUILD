load("//tools/bzl:unsign.bzl", "unsign_jars")

java_library(
  name = 'jgit',
  exports = ['@jgit//jar'],
  runtime_deps = [':ewah'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'ewah',
  exports = ['@ewah//jar'],
  visibility = ['//visibility:public'],
)

unsign_jars(
  name = 'jgit-unsigned',
  deps = [ ':jgit' ]
)
