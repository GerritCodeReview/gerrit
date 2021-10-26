load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")
load("@npm//@bazel/terser:index.bzl", "terser_minified")
load("//tools/bzl:genrule2.bzl", "genrule2")

# The following karma:index.bzl is generated automatically by bazel
# See https://bazelbuild.github.io/rules_nodejs/repositories.html#npm
load("@ui_dev_npm//karma:index.bzl", karma_test_rule = "karma_test")

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
        entry_point = entry_point,
        format = "iife",
        rollup_bin = "//tools/node_tools:rollup-bin",
        sourcemap = "hidden",
        config_file = "//plugins:rollup.config.js",
        deps = [
            "@tools_npm//rollup-plugin-node-resolve",
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

def karma_test(name, data, root):
    """Creates a Karma test target by wrapping the raw karma_test_rule

    It can be used both for the main Gerrit js bundle, but also for plugins. So
    it should be extremely easy to add Karma test capabilities for new plugins.

    We are sharing one karma.conf.js file. If you want to customize that, then
    consider using command line arguments that the config file can process, see
    the `root` argument for an example.

    Args:
      name: The name of the test rule.
      data: The bundle of JavaScript files with the tests included.
      root: where javascript tests files are located (for typescript tests this
        is a location of generated files).
    """

    karma_test_rule(
        name = name,
        size = "enormous",
        args = [
            "start",
            "$(location //polygerrit-ui:karma.conf.js)",
            "--root",
            root,
            "--test-files '*_test.js'",
        ],
        data = data + [
            "@ui_dev_npm//@open-wc/karma-esm",
            "@ui_dev_npm//chai",
            "@ui_dev_npm//karma-chrome-launcher",
            "@ui_dev_npm//karma-mocha",
            "@ui_dev_npm//karma-mocha-reporter",
            "@ui_dev_npm//mocha",
            "//polygerrit-ui:karma.conf.js",
        ],
        tags = ["karma", "local", "manual"],
    )
