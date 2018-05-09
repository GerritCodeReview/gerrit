load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "safehtml_tests",
    srcs = glob(["client/**/*.java"]),
    deps = [
        "//java/com/google/gwtexpui/safehtml",
        "//lib:guava",
        "//lib/gwt:dev",
        "//lib/gwt:user",
        "//lib/truth",
    ],
)
