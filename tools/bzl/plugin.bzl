"""
Build rules for plugins.
"""

load("//:version.bzl", "GERRIT_VERSION")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//tools/bzl:genrule2.bzl", "genrule2")

IN_TREE_BUILD_MODE = True

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
    """Builds a Gerrit plugin.

    Args:
      name: The name of the plugin.
      deps: List of additional dependencies for the plugin.
      provided_deps: List of dependencies that are provided by Gerrit and should not be bundled.
      srcs: List of Java source files for the plugin.
      resources: List of resource files to be included in the plugin JAR.
      resource_jars: List of JARs containing resources.
      runtime_deps: List of runtime dependencies.
      manifest_entries: List of additional lines to add to the plugin's manifest file.
      dir_name: The directory name for the plugin, used in stamping. Defaults to `name`.
      target_suffix: Suffix to append to the final plugin JAR name.
      deploy_env: Environment variables for the deploy JAR.
      **kwargs: Additional arguments passed to the underlying `java_library` and `java_binary` rules.

    This rule creates a deployable .jar file for a Gerrit plugin."""
    java_library(
        name = name + "__plugin",
        srcs = srcs,
        resources = resources,
        deps = provided_deps + deps + PLUGIN_DEPS_NEVERLINK,
        runtime_deps = runtime_deps,
        visibility = ["//visibility:public"],
        **kwargs
    )

    if not dir_name:
        dir_name = name

    java_binary(
        name = "%s__non_stamped" % name,
        deploy_manifest_lines = manifest_entries + [
            "Gerrit-ApiType: plugin",
            "Gerrit-ApiVersion: " + GERRIT_VERSION,
        ],
        main_class = "Dummy",
        runtime_deps = [
            ":%s__plugin" % name,
        ] + runtime_deps + resource_jars,
        deploy_env = deploy_env,
        visibility = ["//visibility:public"],
        **kwargs
    )

    # TODO(davido): Remove manual merge of manifest file when this feature
    # request is implemented: https://github.com/bazelbuild/bazel/issues/2009
    # TODO(davido): Remove manual touch command when this issue is resolved:
    # https://github.com/bazelbuild/bazel/issues/10789
    genrule2(
        name = name + target_suffix,
        stamp = 1,
        srcs = ["%s__non_stamped_deploy.jar" % name],
        cmd = " && ".join([
            "TZ=UTC",
            "export TZ",
            "GEN_VERSION=$$(cat bazel-out/stable-status.txt | grep -w STABLE_BUILD_%s_LABEL | cut -d ' ' -f 2)" % dir_name.upper(),
            "cd $$TMP",
            "unzip -qo $$ROOT/$<",
            "echo \"Implementation-Version: $$GEN_VERSION\n$$(cat META-INF/MANIFEST.MF)\" > META-INF/MANIFEST.MF",
            "find . -exec touch '{}' ';'",
            "zip -Xqr $$ROOT/$@ .",
        ]),
        outs = ["%s%s.jar" % (name, target_suffix)],
        visibility = ["//visibility:public"],
    )
