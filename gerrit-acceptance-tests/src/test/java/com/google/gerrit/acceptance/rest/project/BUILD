load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_project",
    labels = ["rest"],
    deps = [
        ":branch",
        ":project",
    ],
)

java_library(
    name = "branch",
    srcs = [
        "BranchAssert.java",
    ],
    deps = [
        "//gerrit-extension-api:api",
        "//gerrit-server:server",
        "//lib:truth",
    ],
)

java_library(
    name = "project",
    srcs = [
        "ProjectAssert.java",
    ],
    deps = [
        "//gerrit-extension-api:api",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//lib:gwtorm",
        "//lib:truth",
    ],
)
