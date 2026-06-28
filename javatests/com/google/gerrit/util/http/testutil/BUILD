load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "testutil",
    testonly = True,
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
        "//lib/httpcomponents:httpclient",
    ],
)

# EE10 (jakarta.servlet) test fakes. This package contains only the two fakes,
# both of which diverge from a mechanical transform, so testutil-ee10 is just
# the hand-written ee10 variants (no to_jakarta transform needed here). The EE8
# :testutil above is unchanged; its glob does not descend into ee10/ (own BUILD).
java_library(
    name = "testutil-ee10",
    testonly = True,
    visibility = ["//visibility:public"],
    exports = ["//javatests/com/google/gerrit/util/http/testutil/ee10:fakes"],
)
