load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob([
        "*IT.java",
    ]),
    group = "filter",
    labels = ["filter"],
    deps = [
        ":util",
    ],
)

java_library(
    name = "util",
    testonly = True,
    srcs = [
        "FakeMustInitParamsFilter.java",
        "FakeNoInitParamsFilter.java",
    ],
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)
