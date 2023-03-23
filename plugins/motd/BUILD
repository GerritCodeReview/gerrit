load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")
load("//tools/bzl:plugin.bzl", "gerrit_plugin")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "polygerrit_plugin")

gerrit_plugin(
    name = "motd",
    srcs = glob(["java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: motd",
        "Gerrit-Module: com.googlesource.gerrit.plugins.motd.Module",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.motd.HttpModule",
        "Implementation-Title: Plugin motd",
        "Implementation-URL: https://internal/",
    ],
    resource_jars = ["//plugins/motd/web:gr-motd"],
    resources = glob(["src/main/resources/**/*"]),
)
