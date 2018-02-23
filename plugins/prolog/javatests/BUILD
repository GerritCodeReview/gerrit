load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "prolog_tests",
    srcs = glob(["*.java"]),
    resource_strip_prefix = "plugins/prolog/prologtests",
    resources = ["//plugins/prolog/prologtests:gerrit_common_test"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:truth",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//plugins/prolog/lib/prolog:runtime",
        "//plugins/prolog/prolog:gerrit-prolog-common",
    ],
)
