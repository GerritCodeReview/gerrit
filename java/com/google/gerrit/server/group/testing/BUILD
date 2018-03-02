package(default_visibility = ["//visibility:public"])

java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//lib:truth",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
