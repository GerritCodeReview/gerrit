load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "proc_tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    jvm_flags = ["--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/metrics/dropwizard",
        "//lib:truth",
        "//lib/dropwizard:dropwizard-core",
        "//lib/guice",
    ],
)
