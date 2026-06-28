load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "httpd_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/testing:gerrit-junit",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:gson",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:jimfs",
        "//lib:junit",
        "//lib:servlet-api-without-neverlink",
        "//lib:soy",
        "//lib/bouncycastle:bcprov",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/jsoup",
        "//lib/mockito",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)

# --- EE10 (jakarta.servlet) forward-generated test flavour ------------------
# The httpd test sources are forward-generated javax -> jakarta and compiled
# against //java/com/google/gerrit/httpd:httpd-ee10 + the EE10 test fakes. Run
# under --define=flavour=ee10 so //lib/guice resolves to Guice 7 (the cascade).
filegroup(
    name = "test-srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

javax_to_jakarta_srcjar(
    name = "httpd-tests-ee10-srcs",
    direction = "to_jakarta",
    sources = [":test-srcs"],
    src_prefix = "javatests/",
    visibility = ["//visibility:public"],
)

junit_tests(
    name = "httpd_tests_ee10",
    srcs = [":httpd-tests-ee10-srcs"],
    suite_srcs = [":test-srcs"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd:httpd-ee10",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/testing:gerrit-junit",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//javatests/com/google/gerrit/util/http/testutil:testutil-ee10",
        "//lib:gson",
        "//lib:guava",
        "//lib:jakarta-servlet-api-without-neverlink",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:jimfs",
        "//lib:junit",
        "//lib:soy",
        "//lib/bouncycastle:bcprov",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/jsoup",
        "//lib/mockito",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
