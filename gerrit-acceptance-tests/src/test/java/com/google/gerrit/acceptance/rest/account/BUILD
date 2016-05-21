load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

acceptance_tests(
  group = 'rest_account',
  srcs = glob(['*IT.java']),
  deps = [':util'],
  labels = ['rest']
)

java_library(
  name = 'util',
  srcs = [
    'AccountAssert.java',
    'CapabilityInfo.java',
  ],
  deps = [
    '//gerrit-acceptance-tests:lib',
    '//gerrit-reviewdb:server',
    '//lib:gwtorm',
    '//lib:junit',
  ],
  visibility = ['//visibility:public'],
)
