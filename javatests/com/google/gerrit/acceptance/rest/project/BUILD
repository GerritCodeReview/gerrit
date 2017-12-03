load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["CreateProjectIT.java"]),
    group = "create_project",
    labels = ["rest"],
    shard_count = 4,
)

java_library(
    name = "refassert",
    srcs = [
        "RefAssert.java",
    ],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//lib:truth",
    ],
)

java_library(
    name = "project",
    srcs = [
        "ProjectAssert.java",
    ],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//lib:gwtorm",
        "//lib:truth",
    ],
)

java_library(
    name = "push_tag_util",
    testonly = 1,
    srcs = [
        "AbstractPushTag.java",
    ],
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
    ],
)
