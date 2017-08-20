load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_project",
    labels = ["rest"],
    deps = [
        ":project",
        ":push_tag_util",
        ":refassert",
    ],
)

java_library(
    name = "refassert",
    srcs = [
        "RefAssert.java",
    ],
    deps = [
        "//gerrit-server:server",
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
        "//gerrit-server:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
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
        "//gerrit-acceptance-tests:lib",
    ],
)
