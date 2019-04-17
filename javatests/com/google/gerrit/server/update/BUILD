load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "small_tests",
    size = "small",
    srcs = glob(["*.java"]),
    runtime_deps = ["//prolog:gerrit-prolog-common"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gwtorm",
        "//lib:guava",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
