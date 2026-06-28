load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "http_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/util/http",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:junit",
        "//lib:servlet-api-without-neverlink",
        "//lib/mockito",
        "//lib/truth",
    ],
)

# --- EE10 (jakarta.servlet) forward-generated test flavour ------------------
# Run under --define=flavour=ee10 against the EE10 util/http library + fakes.
filegroup(
    name = "test-srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

javax_to_jakarta_srcjar(
    name = "http-tests-ee10-srcs",
    direction = "to_jakarta",
    sources = [":test-srcs"],
    src_prefix = "javatests/",
    visibility = ["//visibility:public"],
)

junit_tests(
    name = "http_tests_ee10",
    srcs = [":http-tests-ee10-srcs"],
    suite_srcs = [":test-srcs"],
    deps = [
        "//java/com/google/gerrit/util/http:http-ee10",
        "//javatests/com/google/gerrit/util/http/testutil:testutil-ee10",
        "//lib:jakarta-servlet-api-without-neverlink",
        "//lib:junit",
        "//lib/mockito",
        "//lib/truth",
    ],
)
