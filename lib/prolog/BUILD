java_library(
  name = 'runtime',
  exports = ['@prolog_runtime//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'compiler',
  exports = ['@prolog_compiler//jar'],
  runtime_deps = [
    ':io',
    ':runtime',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'io',
  exports = ['@prolog_io//jar'],
)

java_library(
  name = 'cafeteria',
  exports = ['@cafeteria//jar'],
  runtime_deps = [
    'io',
    'runtime',
  ],
  visibility = ['//visibility:public'],
)

java_binary(
  name = 'compiler_bin',
  main_class = 'BuckPrologCompiler',
  runtime_deps = [':compiler_lib'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'compiler_lib',
  srcs = ['java/BuckPrologCompiler.java'],
  deps = [
    ':compiler',
    ':runtime',
  ],
  visibility = ['//visibility:public'],
)
