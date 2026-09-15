load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "crypto_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/util/crypto",
        "//lib:junit",
        "//lib/bouncycastle:bcprov",
        "//lib/truth",
    ],
)
