# NOTE: GerritLauncher must be a single, self-contained class. Do not add any
# additional srcs or deps to this rule.
java_library(
  name = 'launcher',
  srcs = ['src/main/java/com/google/gerrit/launcher/GerritLauncher.java'],
  visibility = ['//visibility:public'],
)

java_binary(
  name = 'launcher_bin',
  srcs = ['src/main/java/com/google/gerrit/launcher/GerritLauncher.java'],
  args = ['--use-internal-classloader'],
  main_class = 'com.google.gerrit.launcher.GerritLauncher',
  runtime_deps = [
      '//gerrit-pgm:daemon',
      '//gerrit-war:init',
  ],
)
