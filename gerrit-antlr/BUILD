java_library(
  name = 'query_exception',
  srcs = ['src/main/java/com/google/gerrit/server/query/QueryParseException.java'],
  visibility = ['//visibility:public'],
)

genrule(
  name = 'query_antlr',
  srcs = ['src/main/antlr3/com/google/gerrit/server/query/Query.g'],
  cmd = ' && '.join([
    'ROOT=$$PWD',
    'TMP=$$(mktemp -d)', 
    '$(location //lib/antlr:antlr-tool) -o $$TMP $<',
    'cd $$TMP',
    'zip -qr $$ROOT/$@ *'
  ]),
  tools = ['//lib/antlr:antlr-tool'],
  outs = ['query_antlr.srcjar'],
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
