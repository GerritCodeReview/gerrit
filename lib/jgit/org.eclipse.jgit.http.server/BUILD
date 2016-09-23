load('//tools/bzl:unsign.bzl', 'unsign_jars')

java_library(
  name = 'jgit-servlet-signed',
  exports = ['@jgit_servlet//jar'],
  runtime_deps = ['//lib/jgit/org.eclipse.jgit:jgit'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-jgit'],
)

unsign_jars(
  name = 'jgit-servlet',
  deps = [':jgit-servlet-signed'],
  visibility = ['//visibility:public'],
)
