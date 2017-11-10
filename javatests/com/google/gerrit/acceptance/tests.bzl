load("//tools/bzl:junit.bzl", "junit_tests")

def acceptance_tests(
    group,
    deps = [],
    labels = [],
    vm_args = ['-Xmx256m'],
    **kwargs):
  junit_tests(
    name = group,
    deps = deps + [
        # j/c/g/gerrit/acceptance:lib exports all dependencies that
        # acceptance tests need. Additional dependencies should go
        # there.
        '//java/com/google/gerrit/acceptance:lib',
    ],
    tags = labels + [
      'acceptance',
      'slow',
    ],
    size = "large",
    jvm_flags = vm_args,
    **kwargs
  )
