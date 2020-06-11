load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//:version.bzl", "GERRIT_VERSION")

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
        manifest_entries = [],
        dir_name = None,
        target_suffix = "",
        **kwargs):
    java_library(
        name = name + "__plugin",
        srcs = srcs,
        resources = resources,
        deps = provided_deps + deps + PLUGIN_DEPS_NEVERLINK,
        visibility = ["//visibility:public"],
        **kwargs
    )

    static_jars = []

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
        ] + static_jars,
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
            "unzip -q $$ROOT/$<",
            "echo \"Implementation-Version: $$GEN_VERSION\n$$(cat META-INF/MANIFEST.MF)\" > META-INF/MANIFEST.MF",
            "find . -exec touch '{}' ';'",
            "zip -Xqr $$ROOT/$@ .",
        ]),
        outs = ["%s%s.jar" % (name, target_suffix)],
        visibility = ["//visibility:public"],
    )
