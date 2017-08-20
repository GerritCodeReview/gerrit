load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

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
        "//gerrit-acceptance-tests:lib",
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:gwtorm",
        "//lib:junit",
    ],
)
