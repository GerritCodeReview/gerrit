load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "server_notedb",
    labels = [
        "notedb",
        "server",
    ],
    # TODO(dborowitz): Fix leaks in local disk tests so we can reduce heap size.
    # http://crbug.com/gerrit/8567
    vm_args = ["-Xmx1024m"],
    deps = [
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/server/util/time",
    ],
)
