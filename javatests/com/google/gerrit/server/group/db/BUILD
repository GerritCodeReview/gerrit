load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "db_tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/common/data/testing:common-data-test-util",
        "//java/com/google/gerrit/config",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/extensions/common/testing:common-test-util",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/group/db/testing",
        "//java/com/google/gerrit/server/group/testing",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gerrit/truth",
        "//lib:gwtorm",
        "//lib:truth",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
