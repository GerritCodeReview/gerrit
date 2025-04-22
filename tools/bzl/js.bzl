load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")
load("@npm//@bazel/terser:index.bzl", "terser_minified")
load("//tools/bzl:genrule2.bzl", "genrule2")

ComponentInfo = provider()

def _js_component(ctx):
    dir = ctx.outputs.zip.path + ".dir"
    name = ctx.outputs.zip.basename
    if name.endswith(".zip"):
        name = name[:-4]
    dest = "%s/%s" % (dir, name)
    cmd = " && ".join([
        "TZ=UTC",
        "export TZ",
        "mkdir -p %s" % dest,
        "cp %s %s/" % (" ".join([s.path for s in ctx.files.srcs]), dest),
        "cd %s" % dir,
        "find . -exec touch -t 198001010000 '{}' ';'",
        "zip -Xqr ../%s *" % ctx.outputs.zip.basename,
    ])

    ctx.actions.run_shell(
        inputs = ctx.files.srcs,
        outputs = [ctx.outputs.zip],
        command = cmd,
        mnemonic = "GenJsComponentZip",
    )

    licenses = []
    if ctx.file.license:
        licenses.append(ctx.file.license)

    return [
        ComponentInfo(
            transitive_licenses = depset(licenses),
            transitive_versions = depset(),
            transitive_zipfiles = list([ctx.outputs.zip]),
        ),
    ]

js_component = rule(
    _js_component,
    attrs = {
        "srcs": attr.label_list(allow_files = [".js"]),
        "license": attr.label(allow_single_file = True),
    },
    outputs = {
        "zip": "%{name}.zip",
    },
)

def polygerrit_plugin(name, app, plugin_name = None):
    """Produces plugin file set with minified javascript.

    This rule minifies a plugin javascript file, potentially renames it, and produces a file set.
    Output of this rule is a FileSet with "${plugin_name}.js".

    Args:
      name: String, rule name.
      app: String, the main or root source file. This must be single JavaScript file.
      plugin_name: String, plugin name. ${name} is used if not provided.
    """
    if not plugin_name:
        plugin_name = name

    terser_minified(
        name = plugin_name + ".min",
        sourcemap = False,
        src = app,
    )

    native.genrule(
        name = name + "_rename_js",
        srcs = [plugin_name + ".min"],
        outs = [plugin_name + ".js"],
        cmd = "cp $< $@",
        output_to_bindir = True,
    )

    native.filegroup(
        name = name,
        srcs = [plugin_name + ".js"],
    )

def gerrit_js_bundle(name, entry_point, srcs = []):
    """Produces a Gerrit JavaScript bundle archive.

    This rule bundles and minifies the javascript files of a frontend plugin and
    produces a file archive.
    Output of this rule is an archive with "${name}.jar" with specific layout for
    Gerrit frontend plugins. That archive should be provided to gerrit_plugin
    rule as resource_jars attribute.

    Args:
      name: Rule name.
      srcs: Plugin sources.
      entry_point: Plugin entry_point.
    """

    bundle = name + "-bundle"
    minified = name + ".min"
    main = name + ".js"

    rollup_bundle(
        name = bundle,
        srcs = srcs + [
            "@plugins_npm//:node_modules",
        ],
        args = [
            "--bundleConfigAsCjs=true",
        ],
        entry_point = entry_point,
        format = "iife",
        rollup_bin = "//tools/node_tools:rollup-bin",
        silent = True,
        sourcemap = "hidden",
        config_file = "//plugins:rollup.config.js",
        deps = [
            "@tools_npm//@rollup/plugin-node-resolve",
        ],
    )

    terser_minified(
        name = minified,
        sourcemap = False,
        src = bundle,
    )

    native.genrule(
        name = name + "_rename_js",
        srcs = [minified],
        outs = [main],
        cmd = "cp $< $@",
        output_to_bindir = True,
    )

    genrule2(
        name = name,
        srcs = [main],
        outs = [name + ".jar"],
        cmd = " && ".join([
            "mkdir $$TMP/static",
            "cp $(SRCS) $$TMP/static",
            "cd $$TMP",
            "zip -Drq $$ROOT/$@ -g .",
        ]),
    )

def web_test_runner(name, srcs, data):
    """Creates a Web Test Runner test target.

    It can be used both for the main Gerrit js bundle, but also for plugins. So
    it should be extremely easy to add Web Test Runner test capabilities for new plugins.

    We are sharing one web-test-runner.config.mjs file. If you want to customize that, then
    consider using command line arguments that the config file can process, see
    the `root` argument for an example.

    Args:
      name: The name of the test rule.
      srcs: The shell script to invoke, where you can set command line
        arguments for Web Test Runner and its config.
      data: The bundle of JavaScript files with the tests included.
    """

    native.sh_test(
        name = name,
        size = "enormous",
        srcs = srcs,
        args = [
            "$(location //polygerrit-ui:web_test_runner_bin)",
            "$(location //polygerrit-ui:web-test-runner.config.mjs)",
        ],
        data = data + [
            "//polygerrit-ui:web_test_runner_bin",
            "//polygerrit-ui:web-test-runner.config.mjs",
        ],
        # Should not run sandboxed.
        tags = ["local", "manual"],
    )
