package(default_visibility = ["//visibility:public"])

sh_test(
    name = "test_examples",
    srcs = ["run.sh"],
    args = ["$(JAVABASE)"],
    data = glob(["*.pl"]) + ["//:gerrit.war"],
    toolchains = ["@bazel_tools//tools/jdk:current_host_java_runtime"],
)
