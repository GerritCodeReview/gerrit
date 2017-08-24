load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_group",
    labels = ["api"],
    deps = [
        ":util",
        "//javatests/com/google/gerrit/acceptance/rest/account:util",
    ],
)

java_library(
    name = "util",
    srcs = ["GroupAssert.java"],
    deps = [
        "//gerrit-server:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:gwtorm",
        "//lib:truth",
    ],
)
