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
      '//java/com/google/gerrit/acceptance:lib',
<<<<<<< HEAD
=======
      "//java/com/google/gerrit/server/patchlib",
      "//java/com/google/gerrit/server/config/endpoint",
>>>>>>> f2e880dd87... Split off config REST endpoints from giant server lib
    ],
    tags = labels + [
      'acceptance',
      'slow',
    ],
    size = "large",
    jvm_flags = vm_args,
    **kwargs
  )
