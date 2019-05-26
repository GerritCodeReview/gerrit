load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "prolog_tests",
    srcs = glob(["*.java"]),
    resource_strip_prefix = "prologtests",
    resources = ["//prologtests:gerrit_common_test"],
    runtime_deps = ["//prolog:gerrit-prolog-common"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/mockito",
        "//lib/prolog:runtime",
        "//lib/truth",
    ],
)
