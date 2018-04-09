load("//tools/bzl:genrule2.bzl", "genrule2")
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
        "//lib/auto:auto-value",
        "//lib/jgit/org.eclipse.jgit:jgit",
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
        "//lib/auto:auto-value",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
    ],
)

# ":version" should not be in the dependency graph of the acceptance
# tests to avoid spurious test re-runs. That's because the content of
# //:version.txt is changed when the outcome of `git describe` is changed.
java_library(
    name = "version",
    resources = [":Version"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "gen_version",
    srcs = ["//:version.txt"],
    outs = ["Version"],
    cmd = "cat $< > $@",
)
