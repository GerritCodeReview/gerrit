load('//tools/bzl:genrule2.bzl', 'genrule2')

java_library(
  name = 'query_exception',
  srcs = ['src/main/java/com/google/gerrit/server/query/QueryParseException.java'],
  visibility = ['//visibility:public'],
)

genrule2(
  name = 'query_antlr',
  srcs = ['src/main/antlr3/com/google/gerrit/server/query/Query.g'],
  cmd = ' && '.join([
    '$(location //lib/antlr:antlr-tool) -o $$TMP $<',
    'cd $$TMP',
    '$$ROOT/$(location @bazel_tools//tools/zip:zipper) cC $$ROOT/$@ $$(find .)'
  ]),
  tools = [
    '@bazel_tools//tools/zip:zipper',
    '//lib/antlr:antlr-tool',
  ],
  out = 'query_antlr.srcjar',
)

java_library(
  name = 'query_parser',
  srcs = [':query_antlr'],
  deps = [
    ':query_exception',
    '//lib/antlr:java_runtime',
  ],
  visibility = ['//visibility:public'],
)
