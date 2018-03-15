load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "simple-submit",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: simple-submit",
        "Gerrit-Module: com.googlesource.gerrit.plugins.simple.submit.EasyPreSubmitModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
)
