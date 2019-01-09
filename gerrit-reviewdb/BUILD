load("//tools/bzl:gwt.bzl", "gwt_module")
load("//tools/bzl:junit.bzl", "junit_tests")

package(default_visibility = ["//visibility:public"])

SRC = "src/main/java/com/google/gerrit/reviewdb/"

TESTS = "src/test/java/com/google/gerrit/reviewdb/"

gwt_module(
    name = "client",
    srcs = glob([SRC + "client/**/*.java"]),
    gwt_xml = SRC + "ReviewDB.gwt.xml",
    deps = [
        "//gerrit-extension-api:client",
        "//lib:gwtorm-client",
        "//lib:gwtorm-client_src",
    ],
)

java_library(
    name = "server",
    srcs = glob([SRC + "**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "//gerrit-extension-api:api",
        "//lib:guava",
        "//lib:gwtorm",
    ],
)

junit_tests(
    name = "client_tests",
    srcs = glob([TESTS + "client/**/*.java"]),
    deps = [
        ":client",
        "//gerrit-server:testutil",
        "//lib:gwtorm",
        "//lib:truth",
    ],
)
