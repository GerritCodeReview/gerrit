load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*Test.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/mem",
        "//lib:junit",
        "//lib/guice",
        "//lib/easymock",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
    ],
)
