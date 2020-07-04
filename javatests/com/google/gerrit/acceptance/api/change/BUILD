load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = [
        "api",
        "noci",
    ],
    deps = ["//java/com/google/gerrit/server/util/time"],
) for f in glob(["*IT.java"])]
