java_binary(
  name = 'main_bin',
  main_class = 'Main',
  runtime_deps = [':main_lib'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'main_lib',
  srcs = ['src/main/java/Main.java'],
  deps = ['//gerrit-launcher:launcher'],
  visibility = ['//visibility:public'],
)
