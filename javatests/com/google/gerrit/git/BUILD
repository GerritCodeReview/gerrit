load("//tools/bzl:junit.bzl", "junit_tests")

MEDIUM_TESTS = ["RefUpdateUtilRepoTest.java"]

junit_tests(
    name = "medium_tests",
    size = "medium",
    timeout = "short",
    srcs = MEDIUM_TESTS,
    tags = ["no_windows"],
    deps = [
        "//java/com/google/gerrit/git",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:junit",
        "//lib/truth",
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
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/truth",
    ],
)
