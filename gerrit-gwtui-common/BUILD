load("//tools/bzl:java.bzl", "java_library2")
load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:gwt.bzl", "gwt_module")

EXPORTED_DEPS = [
    "//gerrit-common:client",
    "//gerrit-gwtexpui:Clippy",
    "//gerrit-gwtexpui:GlobalKey",
    "//gerrit-gwtexpui:Progress",
    "//gerrit-gwtexpui:SafeHtml",
    "//gerrit-gwtexpui:UserAgent",
]

DEPS = ["//lib/gwt:user-neverlink"]

SRC = "src/main/java/com/google/gerrit/"

gwt_module(
    name = "client",
    srcs = glob(["src/main/**/*.java"]),
    exported_deps = EXPORTED_DEPS,
    gwt_xml = SRC + "GerritGwtUICommon.gwt.xml",
    resources = glob(
        ["src/main/**/*"],
        exclude = [SRC + "client/**/*.java"] + [
            SRC + "GerritGwtUICommon.gwt.xml",
        ],
    ),
    visibility = ["//visibility:public"],
    deps = DEPS,
)

java_library2(
    name = "client-lib",
    srcs = glob(["src/main/**/*.java"]),
    exported_deps = EXPORTED_DEPS,
    resources = glob(["src/main/**/*"]),
    visibility = ["//visibility:public"],
    deps = DEPS,
)

java_library(
    name = "diffy_logo",
    data = [
        "//lib:LICENSE-CC-BY3.0-unported",
        "//lib:LICENSE-diffy",
    ],
    resources = glob(["src/main/resources/com/google/gerrit/client/diffy*.png"]),
    visibility = ["//visibility:public"],
)

