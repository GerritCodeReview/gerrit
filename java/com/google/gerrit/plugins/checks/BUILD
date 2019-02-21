package_group(
    name = "visibility",
    packages = [
        "//java/com/google/gerrit/plugins/checks/...",
        "//javatests/com/google/gerrit/plugins/checks/...",
    ],
)

package(default_visibility = [":visibility"])

load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "checks",
    srcs = glob(["**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: delete-project",
        "Gerrit-Module: com.google.gerrit.plugins.checks.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.api.HttpModule",
    ],
    deps = [
        "//java/com/google/gerrit/server/api",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
    ],
)
