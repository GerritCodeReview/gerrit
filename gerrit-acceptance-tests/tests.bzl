load('//tools/bzl:junit.bzl', 'junit_tests')

# These are needed as workaround for the 'verify: false' bug in Jcraft SSH library
BOUNCYCASTLE = [
  '//lib/bouncycastle:bcpkix',
  '//lib/bouncycastle:bcpg',
]

def acceptance_tests(
    group,
    srcs,
    flaky = 0,
    deps = [],
    labels = [],
    source_under_test = [],#unused
    vm_args = ['-Xmx256m']):
#  from os import environ, path
#  if not environ.get('NO_BOUNCYCASTLE'):
#    deps = BOUNCYCASTLE + deps
#  if path.exists('/dev/urandom'):
#    vm_args = vm_args + ['-Djava.security.egd=file:/dev/./urandom']
  junit_tests(
    name = group,
    srcs = srcs,
    flaky = flaky,
    deps = ['//gerrit-acceptance-tests:lib'] + deps,
    tags = labels + [
      'acceptance',
      'slow',
    ],
    jvm_flags = vm_args,
  )
