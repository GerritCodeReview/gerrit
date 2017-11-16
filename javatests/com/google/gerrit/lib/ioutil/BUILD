load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "ioutil_tests",
    size = "small",
    srcs = glob(
        ["**/*.java"],
    ),
    resource_strip_prefix = "resources",
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/lib/ioutil",
    ],
)
