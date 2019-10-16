load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_account",
    labels = ["rest"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/account/externalids/testing",
    ],
)

java_library(
    name = "util",
    testonly = True,
    srcs = [
        "AccountAssert.java",
        "CapabilityInfo.java",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/entities",
        "//lib:junit",
    ],
)
