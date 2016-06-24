load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

acceptance_tests(
  group = 'pgm',
  srcs = glob(['*IT.java']),
  source_under_test = ['//gerrit-pgm:pgm'],
  labels = ['pgm'],
)
