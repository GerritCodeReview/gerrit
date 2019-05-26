load("@rules_java//java:defs.bzl", "java_library")

package(default_testonly = 1)

java_library(
    name = "project",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/acceptance:function",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/commons:lang",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
