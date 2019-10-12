load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "git_tests",
    size = "small",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/server/util/git",
        "//lib:gson",
        "//lib:guava",
        "//lib:guava-retrying",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:protobuf",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/guice",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
    ],
)
