load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "schema",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/gpg",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/logging",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-archive",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/commons:dbcp",
        "//lib/flogger:api",
        "//lib/guice",
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
        "//lib/jackson:jackson-annotations",
        "//lib/jackson:jackson-databind",
        "//lib/log:api",
        "//lib/log:log4j",
=======
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
    ],
)
