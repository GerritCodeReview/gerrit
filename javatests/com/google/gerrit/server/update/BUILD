load("//tools/bzl:junit.bzl", "junit_tests")

MEDIUM_TESTS = ["RefUpdateUtilRepoTest.java"]

junit_tests(
    name = "medium_tests",
    size = "medium",
    timeout = "short",
    srcs = MEDIUM_TESTS,
    deps = [
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib:junit",
        "//lib:truth",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)

junit_tests(
    name = "small_tests",
    size = "small",
    srcs = glob(
        ["*.java"],
        exclude = MEDIUM_TESTS,
    ),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:truth",
        "//lib:truth-java8-extension",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
