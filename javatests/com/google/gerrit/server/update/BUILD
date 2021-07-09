load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "small_tests",
    size = "small",
    srcs = glob([
        "BatchUpdateTest.java",
        "RepoViewTest.java",
    ]),
    runtime_deps = [
        "//java/com/google/gerrit/lucene",
        "//prolog:gerrit-prolog-common",
    ],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/guice",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)

junit_tests(
    name = "large_tests",
    size = "large",
    srcs = glob(["BatchOpsExecutorTest.java"]),
    runtime_deps = [
        "//java/com/google/gerrit/lucene",
        "//prolog:gerrit-prolog-common",
    ],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/guice",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
