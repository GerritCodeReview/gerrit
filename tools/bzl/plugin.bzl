load("@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl", _gerrit_plugin = "gerrit_plugin")

# Keep legacy constants for in-tree consumers.
PLUGIN_DEPS = ["//plugins:plugin-lib"]

PLUGIN_DEPS_NEVERLINK = ["//plugins:plugin-lib-neverlink"]

PLUGIN_TEST_DEPS = [
    "//java/com/google/gerrit/acceptance:lib",
    "//lib/bouncycastle:bcpg",
    "//lib/bouncycastle:bcpkix",
    "//lib/bouncycastle:bcprov",
]

def gerrit_plugin(
        name,
        deps = [],
        provided_deps = [],
        srcs = [],
        resources = [],
        resource_jars = [],
        runtime_deps = [],
        manifest_entries = [],
        dir_name = None,
        target_suffix = "",
        deploy_env = [],
        **kwargs):
    """Compatibility wrapper for bazlets' gerrit_plugin.

    Preserves the legacy macro signature and constants for in-tree builds,
    while delegating the implementation to bazlets.
    """
    _gerrit_plugin(
        name = name,
        deps = deps,
        provided_deps = provided_deps,
        srcs = srcs,
        resources = resources,
        resource_jars = resource_jars,
        runtime_deps = runtime_deps,
        manifest_entries = manifest_entries,
        dir_name = dir_name,
        target_suffix = target_suffix,
        deploy_env = deploy_env,
        **kwargs
    )
