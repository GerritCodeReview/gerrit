load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*.java"]),
    group = "globalrefdb",
    deps = [
        "//java/com/google/gerrit/globalrefdb",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/truth",
        "//lib:guava",
        "//lib:junit",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
    ],
)
