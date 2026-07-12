load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_tests")

flavoured_tests(
    name = "http_tests",
    srcs = glob(["**/*.java"]),
    canonical = "ee11",
    default_flavour = "ee11",
    direction = "to_javax",
    deps = [
        "//java/com/google/gerrit/util/http",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:junit",
        "//lib:servlet-api-without-neverlink",
        "//lib/mockito",
        "//lib/truth",
    ],
)
