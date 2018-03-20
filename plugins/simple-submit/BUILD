load("//tools/bzl:plugin.bzl", "gerrit_plugin")
load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_DEPS")

gerrit_plugin(
    name = "simple-submit",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: simple-submit",
        "Gerrit-Module: com.googlesource.gerrit.plugins.simple.submit.EasyPreSubmitModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = PLUGIN_DEPS + [
        "@com_google_re2j//jar",
    ],
)
