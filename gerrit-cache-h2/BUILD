load('//tools/bzl:junit.bzl', 'junit_tests')

java_library(
  name = 'cache-h2',
  srcs = glob(['src/main/java/**/*.java']),
  deps = [
    '//gerrit-common:server',
    '//gerrit-extension-api:api',
    '//gerrit-server:server',
    '//lib:guava',
    '//lib:h2',
    '//lib/guice:guice',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib/log:api',
  ],
  visibility = ['//visibility:public'],
)

junit_tests(
  name = 'tests',
  srcs = glob(['src/test/java/**/*.java']),
  deps = [
    ':cache-h2',
    '//gerrit-server:server',
    '//lib:guava',
    '//lib:h2',
    '//lib/guice:guice',
    '//lib:junit',
  ],
)
