
[java_library(
  name = n,
  exports = ['@%s//jar' % n],
  data = ['//lib:LICENSE-antlr'],
) for n in [
  'antlr27',
  'stringtemplate',
]]

java_library(
  name = 'java_runtime',
  exports = ['@java_runtime//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-antlr'],
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
  data = ['//lib:LICENSE-antlr'],
)
