load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = [
        "api",
    ],
    deps = ["//java/com/google/gerrit/server/util/time",
            "//javatests/com/google/gerrit/acceptance/server/change:util"],
    visibility = ["//visibility:public"]
) for f in glob(["*IT.java"])]
