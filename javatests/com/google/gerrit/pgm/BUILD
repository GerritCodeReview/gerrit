load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_junit_tests", "flavoured_tests")
load("//tools/bzl:license.bzl", "license_test")

# Cookie.setAttribute/getAttribute exist only in Servlet 6 (jakarta), so the
# SameSiteFilter tests cannot have a generated javax twin; they compile and
# run only under the ee11 flavour (the ee8 overlay filter uses the
# setComment fallback instead).
SERVLET6_ONLY_TESTS = [
    "http/jetty/SameSiteFilterServerTest.java",
    "http/jetty/SameSiteFilterTest.java",
]

flavoured_tests(
    name = "pgm_tests",
    srcs = glob(
        ["**/*.java"],
        exclude = SERVLET6_ONLY_TESTS,
    ),
    canonical = "ee11",
    deps = [
        "//java/com/google/gerrit/pgm/http/jetty",
        "//java/com/google/gerrit/pgm/init/api",
        "//java/com/google/gerrit/pgm/util",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/securestore/testing",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:junit",
        "//lib:servlet-api",
        "//lib/guice",
        "//lib/jetty:server",
        "//lib/jetty:servlet",
        "//lib/mockito",
        "//lib/truth",
    ],
)

license_test(
    name = "pgm_license_test",
    target = "//java/com/google/gerrit/pgm",
)

flavoured_junit_tests(
    name = "servlet6_only_tests-ee11",
    srcs = glob(SERVLET6_ONLY_TESTS),
    flavour = "ee11",
    deps = [
        "//java/com/google/gerrit/pgm/http/jetty",
        "//java/com/google/gerrit/pgm/init/api",
        "//java/com/google/gerrit/pgm/util",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/securestore/testing",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:junit",
        "//lib:servlet-api",
        "//lib/guice",
        "//lib/jetty:server",
        "//lib/jetty:servlet",
        "//lib/mockito",
        "//lib/truth",
    ],
)
