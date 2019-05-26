load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "ioutil",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//lib:automaton",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit.archive:jgit-archive",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
