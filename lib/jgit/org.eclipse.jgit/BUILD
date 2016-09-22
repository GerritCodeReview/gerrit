load('//tools/bzl:genrule2.bzl', 'genrule2')

genrule2(
  name = 'jgit-unsigned-file',
  srcs = ['@jgit//jar'],
  cmd = ' && '.join([
    'cp $< $@',
    "zip -d $@ 'META-INF/*.SF' 'META-INF/*.RSA'",
  ]),
  out = 'jgit-unsigned-file.jar',
  visibility = ['//visibility:public'],
)

java_import(
  name = 'jgit-unsigned',
  jars = [':jgit-unsigned-file'],
  visibility = ['//visibility:public'],
)

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
