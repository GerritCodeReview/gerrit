load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = ["api"],
    deps = [
        ":revision-diff-it",
        "//javatests/com/google/gerrit/acceptance/server/change:util",
    ],
) for f in glob(["*IT.java"])]

# This is needed because RevisionDiffIT has subclasses that depend on it
java_library(
    name = "revision-diff-it",
    srcs = ["RevisionDiffIT.java"],
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)
