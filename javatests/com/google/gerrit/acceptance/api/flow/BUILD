load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_flow",
    labels = ["api"],
    deps = [
        ":flow-test-util",
    ],
)

java_library(
    name = "flow-test-util",
    testonly = True,
    srcs = glob(["FlowTestUtil.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib:guava",
    ],
)
