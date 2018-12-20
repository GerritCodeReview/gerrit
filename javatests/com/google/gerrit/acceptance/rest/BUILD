load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_bindings",
    labels = ["rest"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/logging",
    ],
)

java_library(
    name = "util",
    testonly = True,
    srcs = [
        "AbstractRestApiBindingsTest.java",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//lib/commons:lang",
    ],
)
