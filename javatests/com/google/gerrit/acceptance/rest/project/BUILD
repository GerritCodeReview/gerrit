load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

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
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib/truth",
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
        "//lib:guava",
        "//lib:gwtorm",
        "//lib/truth",
    ],
)

java_library(
    name = "push_tag_util",
    testonly = True,
    srcs = [
        "AbstractHttpPushTag.java",
        "AbstractPushTag.java",
        "AbstractSshPushTag.java",
    ],
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
    ],
)
