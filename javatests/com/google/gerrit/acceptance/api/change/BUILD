load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

PLUGIN_UTIL_SRCS = ["AbstractPluginFieldsTest.java"]

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = PLUGIN_UTIL_SRCS,
    ),
    group = "api_change",
    labels = [
        "api",
        "noci",
    ],
    deps = [
        ":plugin_util",
        "//java/com/google/gerrit/server/util/time",
    ],
)

java_library(
    name = "plugin_util",
    srcs = PLUGIN_UTIL_SRCS,
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
    ],
)
