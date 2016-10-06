load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

acceptance_tests(
  group = 'rest_project',
  srcs = glob(['BeforeTestIT.java']),
  deps = [
    ':branch',
    ':project',
  ],
  labels = ['rest'],
#  shard_count=30,
)

java_library(
  name = 'branch',
  srcs = [
    'BranchAssert.java',
  ],
  deps = [
    '//lib:truth',
    '//gerrit-extension-api:api',
    '//gerrit-server:server',
  ],
)

java_library(
  name = 'project',
  srcs = [
    'ProjectAssert.java',
  ],
  deps = [
    '//gerrit-extension-api:api',
    '//gerrit-reviewdb:server',
    '//gerrit-server:server',
    '//lib:gwtorm',
    '//lib:truth',
  ],
)
