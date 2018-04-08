load("//tools/bzl:junit.bzl", "junit_tests")

CUSTOM_TRUTH_SUBJECTS = glob([
    "**/*Subject.java",
])

java_library(
    name = "custom-truth-subjects",
    testonly = 1,
    srcs = CUSTOM_TRUTH_SUBJECTS,
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/truth",
        "//lib:truth",
    ],
)

junit_tests(
    name = "server_tests",
    size = "large",
    srcs = glob(
        ["**/*.java"],
        exclude = CUSTOM_TRUTH_SUBJECTS,
    ),
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/server"],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//lib/bouncycastle:bcprov",
    ],
    deps = [
        ":custom-truth-subjects",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/config",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/extensions/common/testing:common-test-util",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/group/testing",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gerrit/truth",
        "//java/org/eclipse/jgit:server",
        "//lib:grappa",
        "//lib:gson",
        "//lib:guava-retrying",
        "//lib:gwtorm",
        "//lib:truth-java8-extension",
        "//lib/auto:auto-value",
        "//lib/commons:codec",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
