load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = [
        "api",
    ],
    deps = ["//java/com/google/gerrit/server/util/time"],
) for f in glob(["*IT.java"])]

java_library(
    name = "change_tests",
    testonly = True,
    srcs = ["ChangeIT.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/acceptance:lib"
    ],
)
