load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = ["rest"],
    deps = [
        ":labelassert",
        ":project",
        ":push_tag_util",
        ":refassert",
        "//lib/commons:lang",
    ],
) for f in glob(["*IT.java"])]

java_library(
    name = "labelassert",
    srcs = [
        "LabelAssert.java",
    ],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib/truth",
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
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib:guava",
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
