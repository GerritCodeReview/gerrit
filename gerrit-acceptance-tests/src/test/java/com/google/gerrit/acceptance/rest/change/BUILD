load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

SUBMIT_UTIL_SRCS = [
  'AbstractSubmit.java',
  'AbstractSubmitByMerge.java',
]

SUBMIT_TESTS = glob(['Submit*IT.java'])
OTHER_TESTS = glob(['*IT.java'], exclude = SUBMIT_TESTS)

acceptance_tests(
  group = 'rest_change_other',
  srcs = OTHER_TESTS,
  deps = [
    ':submit_util',
    '//lib/joda:joda-time',
  ],
  labels = ['rest'],
)

acceptance_tests(
  group = 'rest_change_submit',
  srcs = SUBMIT_TESTS,
  deps = [
    ':submit_util',
  ],
  labels = ['rest'],
)

java_library(
  name = 'submit_util',
  srcs = SUBMIT_UTIL_SRCS,
  deps = [
    '//gerrit-acceptance-tests:lib',
  ],
)
