load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "config_tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    deps = [
        # TODO(davido): Remove dependency on server:server (HostPlatform.java)
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/extensions/common/testing:common-test-util",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/config",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/metrics/dropwizard",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//lib:truth",
        "//lib:truth-java8-extension",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
