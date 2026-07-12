load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:license.bzl", "license_test")

# The SameSiteFilter tests stay a separate suite (a remnant of the
# two-flavour era, when Servlet 6 Cookie.setAttribute had no javax twin);
# they carry their own embedded-Jetty server deps.
SERVLET6_ONLY_TESTS = [
    "http/jetty/SameSiteFilterServerTest.java",
    "http/jetty/SameSiteFilterTest.java",
]

junit_tests(
    name = "pgm_tests",
    srcs = glob(
        ["**/*.java"],
        exclude = SERVLET6_ONLY_TESTS,
    ),
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

junit_tests(
    name = "servlet6_only_tests",
    srcs = glob(SERVLET6_ONLY_TESTS),
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
