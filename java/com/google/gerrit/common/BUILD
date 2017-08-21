load("//tools/bzl:gwt.bzl", "gwt_module")

ANNOTATIONS = [
    "Nullable.java",
    "audit/Audit.java",
    "auth/SignInRequired.java",
]

java_library(
    name = "annotations",
    srcs = ANNOTATIONS,
    visibility = ["//visibility:public"],
)

gwt_module(
    name = "client",
    srcs = glob(["**/*.java"]),
    exported_deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/prettify:client",
        "//lib:guava",
        "//lib:gwtorm_client",
        "//lib:servlet-api-3_1",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/joda:joda-time",
        "//lib/log:api",
    ],
    gwt_xml = "Common.gwt.xml",
    visibility = ["//visibility:public"],
)

java_library(
    name = "server",
    srcs = glob(
        ["**/*.java"],
        exclude = ANNOTATIONS,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/prettify:server",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/org/eclipse/jgit:server",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib:servlet-api-3_1",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/joda:joda-time",
        "//lib/log:api",
    ],
)
