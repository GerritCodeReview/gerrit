load("//tools/bzl:junit.bzl", "junit_tests")

SCHEMA_UPGRADE_TEST_SRCS = [
    "Schema_150_to_151_Test.java",
    "Schema_159_to_160_Test.java",
    "Schema_161_to_162_Test.java",
]

junit_tests(
    name = "small_tests",
    size = "small",
    srcs = glob(
        ["*.java"],
        exclude = SCHEMA_UPGRADE_TEST_SRCS,
    ),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:gwtorm",
        "//lib:truth",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)

java_test(
    name = "Schema_150_to_151_Test",
    size = "large",
    srcs = ["Schema_150_to_151_Test.java"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:gwtorm",
        "//lib:truth",
        "//lib/guice",
    ],
)

java_test(
    name = "Schema_159_to_160_Test",
    size = "large",
    srcs = ["Schema_159_to_160_Test.java"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:truth",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)

java_test(
    name = "Schema_161_to_162_Test",
    size = "large",
    srcs = ["Schema_161_to_162_Test.java"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:gwtorm",
        "//lib:truth",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
