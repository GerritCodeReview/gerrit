load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")
load("@npm//@bazel/terser:index.bzl", "terser_minified")
load("//tools/bzl:genrule2.bzl", "genrule2")

ComponentInfo = provider()

def get_ts_compiled_path(outdir, file_name):
    """Calculates the typescript output path for a file_name.

    Args:
      outdir: the typescript output directory (relative to polygerrit-ui/app/)
      file_name: the original file name (relative to polygerrit-ui/app/)

    Returns:
      String - the path to the file produced by the typescript compiler
    """
    if not outdir.endswith("/") and len(outdir) > 0:
        outdir = outdir + "/"
    if file_name.endswith(".js"):
        return outdir + file_name
    if file_name.endswith(".ts"):
        return outdir + file_name[:-2] + "js"
    fail("The file " + file_name + " has unsupported extension")

def get_ts_output_files(outdir, srcs):
    """Calculates the files paths produced by the typescript compiler

    Args:
      outdir: the typescript output directory (relative to polygerrit-ui/app/)
      srcs: list of input files (all paths relative to polygerrit-ui/app/)

    Returns:
      List of strings
    """
    result = []
    for f in srcs:
        if f.endswith(".d.ts"):
            continue
        result.append(get_ts_compiled_path(outdir, f))
    return result

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

def compile_plugin_ts(name, srcs, ts_outdir = "", additional_deps = [], tags = []):
    """Compiles srcs files with the typescript compiler. The following
    dependencies are always passed:
      the file specified by the ts_project argument
      :tsconfig.json"
      @ui_npm//:node_modules,
    If compilation succeed, the file name+".success" is created. This is useful
    for wrapping compilation in bazel test rules.

    Args:
      name: rule name
      srcs: list of input files (.js, .d.ts and .ts)
      ts_outdir: typescript output directory; ignored if emitJS is True
      additional_deps: list of additional dependencies for compilation

    Returns:
      The list of compiled JS files if emitJS is True; otherwise returns an
      empty list
    """
    ts_rule_name = name + "_ts_compiled"

    # List of files produced by the typescript compiler
    generated_js = get_ts_output_files(ts_outdir, srcs)

    all_srcs = srcs + [
        ":tsconfig.json",
        "@plugins_npm//:node_modules",
    ] + additional_deps

    success_out = name + ".success"

    # Run the compiler
    native.genrule(
        name = ts_rule_name,
        srcs = all_srcs,
        outs = generated_js + [success_out],
        cmd = " && ".join([
            "$(location //tools/node_tools:tsc-bin) --project $(location :tsconfig.json)" +
            " --outdir $(RULEDIR)/{}".format(ts_outdir) +
            " --baseUrl ./external/plugins_npm/node_modules/",
            "touch $(location {})".format(success_out),
        ]),
        tools = ["//tools/node_tools:tsc-bin"],
        tags = tags,
    )

    return generated_js

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

    native.filegroup(
        name = name + "-full-src",
        srcs = srcs + [
            "@plugins_npm//:node_modules",
        ],
    )

    rollup_bundle(
        name = bundle,
        config_file = ":rollup.config.js",
        srcs = [name + "-full-src"],
        entry_point = entry_point,
        rollup_bin = "//tools/node_tools:rollup-bin",
        sourcemap = "hidden",
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
