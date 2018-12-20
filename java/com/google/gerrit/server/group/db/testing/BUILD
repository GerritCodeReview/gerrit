package(default_visibility = ["//visibility:public"])

java_library(
    name = "testing",
    testonly = True,
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
