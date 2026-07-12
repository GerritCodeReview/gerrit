load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob([
        "*IT.java",
    ]),
    canonical = "ee11",
    default_flavour = "ee11",
    direction = "to_javax",
    group = "filter",
    labels = ["filter"],
    deps = [
        ":util",
    ],
)

flavoured_library(
    name = "util",
    testonly = True,
    srcs = [
        "FakeMustInitParamsFilter.java",
        "FakeNoInitParamsFilter.java",
    ],
    canonical = "ee11",
    default_flavour = "ee11",
    direction = "to_javax",
    src_prefix = "javatests/",
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//lib:servlet-api",
    ],
)
