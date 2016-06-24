load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

acceptance_tests(
  group = 'git',
  srcs = glob(['*IT.java']),
  deps = [
    ':submodule_util',
    ':push_for_review',
  ],
  labels = ['git'],
)

java_library(
  name = 'push_for_review',
  srcs = ['AbstractPushForReview.java'],
  deps = [
    '//gerrit-acceptance-tests:lib',
    '//lib/joda:joda-time',
  ],
)

java_library(
  name = 'submodule_util',
  srcs = ['AbstractSubmoduleSubscription.java',],
  deps = ['//gerrit-acceptance-tests:lib',]
)
