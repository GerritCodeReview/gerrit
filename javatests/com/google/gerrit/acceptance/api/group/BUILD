load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_group",
    labels = ["api"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/group/db/testing",
        "//java/com/google/gerrit/server/group/testing",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/truth",
        "//javatests/com/google/gerrit/acceptance/rest/account:util",
    ],
)

java_library(
    name = "util",
    srcs = ["GroupAssert.java"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib:gwtorm",
        "//lib/truth",
    ],
)
