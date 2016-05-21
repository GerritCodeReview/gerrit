load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

acceptance_tests(
  group = 'ssh',
  srcs = glob(['*IT.java']),
  deps = ['//lib/commons:compress'],
  labels = ['ssh'],
)
