package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "rest",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/org/eclipse/jgit:server",
        "//lib:args4j",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:servlet-api-3_1",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
    ],
)
