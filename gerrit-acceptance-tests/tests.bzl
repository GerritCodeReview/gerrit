def acceptance_tests(
    pkg,
    srcs,
    deps = [],
    labels = [],
    vm_args = ['-Xmx256m'],
    **kwargs):
  [native.java_test(
    name = src[:len(src)-len('.java')],
    test_class = "%s.%s" % (pkg, src[:len(src)-len('.java')]),
    srcs = [src],
    deps = deps + [
      "//gerrit-acceptance-tests:lib",
    ],
    tags = labels + [
      "acceptance",
      "slow",
    ],
    size = "large",
    jvm_flags = vm_args,
    **kwargs) for src in srcs]
