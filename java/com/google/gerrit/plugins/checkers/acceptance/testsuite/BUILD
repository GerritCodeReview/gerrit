package(
    default_testonly = True,
    default_visibility = ["//java/com/google/gerrit/plugins/checkers:visibility"],
)

java_library(
    name = "testsuite",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/plugins/checkers",
    ],
)
