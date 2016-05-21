
[java_library(
  name = n,
  exports = ['@%s//jar' % n],
) for n in [
  'antlr27',
  'stringtemplate',
]]

java_library(
  name = 'java_runtime',
  exports = ['@java_runtime//jar'],
  visibility = ['//visibility:public'],
)

java_binary(
  name = 'antlr-tool',
  main_class = 'org.antlr.Tool',
  runtime_deps = [':tool'],
  visibility = ['//gerrit-antlr:__pkg__'],
)

java_library(
  name = 'tool',
  exports = ['@org_antlr//jar'],
  runtime_deps = [
    ':antlr27',
    ':java_runtime',
    ':stringtemplate',
  ],
)
