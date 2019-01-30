package(default_visibility = ["//visibility:public"])

java_library(
    name = "testing",
    testonly = True,
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/truth",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
    ],
)
