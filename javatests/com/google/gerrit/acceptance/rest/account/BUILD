load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_account",
    labels = ["rest"],
    deps = [":util"],
)

java_library(
    name = "util",
    testonly = 1,
    srcs = [
        "AccountAssert.java",
        "CapabilityInfo.java",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//javatests/com/google/gerrit/acceptance:lib",
        "//lib:gwtorm",
        "//lib:junit",
    ],
)
