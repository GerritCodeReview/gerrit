package(default_visibility = ["//visibility:public"])

sh_test(
    name = "test_examples",
    srcs = ["run.sh"],
    args = ["$(JAVA)"],
    data = glob(["*.pl"]) + [
        "//:gerrit.war",
        "@bazel_tools//tools/jdk:current_host_java_runtime",
    ],
    toolchains = ["@bazel_tools//tools/jdk:current_host_java_runtime"],
)
