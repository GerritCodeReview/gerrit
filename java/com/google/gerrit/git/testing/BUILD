package(default_testonly = 1)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:truth",
        "//lib:truth-java8-extension",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
