package(default_testonly = True)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
