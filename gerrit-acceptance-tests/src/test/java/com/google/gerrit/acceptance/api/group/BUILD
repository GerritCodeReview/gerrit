load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_group",
    labels = ["api"],
    deps = [
        ":util",
        "//gerrit-acceptance-tests/src/test/java/com/google/gerrit/acceptance/rest/account:util",
    ],
)

java_library(
    name = "util",
    srcs = ["GroupAssert.java"],
    deps = [
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//java/com/google/gerrit/extensions:api",
        "//lib:gwtorm",
        "//lib:truth",
    ],
)
