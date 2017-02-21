package(
    default_visibility = ["//visibility:public"],
)

load("//tools/bzl:gwt.bzl", "gwt_module")
load("//tools/bzl:junit.bzl", "junit_tests")

SRC = "src/main/java/com/google/gerrit/reviewdb/"

TESTS = "src/test/java/com/google/gerrit/reviewdb/"

gwt_module(
    name = "client",
    srcs = glob([SRC + "client/**/*.java"]),
    gwt_xml = SRC + "ReviewDB.gwt.xml",
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-extension-api:client",
        "//lib:gwtorm_client",
        "//lib:gwtorm_client_src",
    ],
)

java_library(
    name = "server",
    srcs = glob([SRC + "**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-extension-api:api",
        "//lib:guava",
        "//lib:gwtorm",
    ],
)

