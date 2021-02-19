load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = ["server"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/util/time",
    ],
) for f in glob(["*IT.java"])]

java_library(
    name = "util",
    srcs = ["CommentsUtil.java"],
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "@guava//jar",
    ],
)
