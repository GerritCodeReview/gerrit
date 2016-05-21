load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

FLAKY_TEST_CASES=['ProjectWatchIT.java']

acceptance_tests(
  group = 'server_project',
  srcs = glob(['*IT.java'], exclude=FLAKY_TEST_CASES),
  labels = ['server'],
)

acceptance_tests(
  group = 'server_project_flaky',
  flaky = 1,
  srcs = FLAKY_TEST_CASES,
  labels = ['server', 'flaky'],
)
