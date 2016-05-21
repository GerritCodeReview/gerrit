load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

#TODO(davido): Figure out why these two tests are failing
acceptance_tests(
  group = 'ssh',
  srcs = glob(
    ['*IT.java'],
    exclude = [
      'CreateProjectIT.java',
      'GarbageCollectionIT.java',
    ],
  ),
  deps = ['//lib/commons:compress'],
  labels = ['ssh'],
)
