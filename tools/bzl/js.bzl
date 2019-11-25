load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library")

NPMJS = "NPMJS"

GERRIT = "GERRIT:"

def _npm_tarball(name):
    return "%s@%s.npm_binary.tgz" % (name, "1.0.0")

ComponentInfo = provider()

_common_attrs = {
    "deps": attr.label_list(providers = [ComponentInfo]),
}

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
        mnemonic = "GenBowerZip",
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
    attrs = dict(_common_attrs.items() + {
        "srcs": attr.label_list(allow_files = [".js"]),
        "license": attr.label(allow_single_file = True),
    }.items()),
    outputs = {
        "zip": "%{name}.zip",
    },
)

def _bundle_impl(ctx):
    """Groups a set of .html and .js together in a zip file.

    Outputs:
      NAME-versions.json:
        a JSON file containing a PKG-NAME => PKG-NAME#VERSION mapping for the
        transitive dependencies.
    NAME.zip:
      a zip file containing the transitive dependencies for this bundle.
    """

    # intermediate artifact if split is wanted.
    if ctx.attr.split:
        bundled = ctx.actions.declare_file(ctx.outputs.html.path + ".bundled.html")
    else:
        bundled = ctx.outputs.html
    destdir = ctx.outputs.html.path + ".dir"
    zips = [z for d in ctx.attr.deps for z in d[ComponentInfo].transitive_zipfiles.to_list()]

    # We are splitting off the package dir from the app.path such that
    # we can set the package dir as the root for the bundler, which means
    # that absolute imports are interpreted relative to that root.
    pkg_dir = ctx.attr.pkg.lstrip("/")
    app_path = ctx.file.app.path
    app_path = app_path[app_path.index(pkg_dir) + len(pkg_dir):]

    hermetic_npm_binary = " ".join([
        "python",
        "$p/" + ctx.file._run_npm.path,
        "$p/" + ctx.file._bundler_archive.path,
        "--inline-scripts",
        "--inline-css",
        "--strip-comments",
        "--out-file",
        "$p/" + bundled.path,
        "--root",
        pkg_dir,
        app_path,
    ])

    cmd = " && ".join([
        # unpack dependencies.
        "export PATH",
        "p=$PWD",
        "rm -rf %s" % destdir,
        "mkdir -p %s/%s/bower_components" % (destdir, pkg_dir),
        "for z in %s; do unzip -qd %s/%s/bower_components/ $z; done" % (
            " ".join([z.path for z in zips]),
            destdir,
            pkg_dir,
        ),
        "tar -cf - %s | tar -C %s -xf -" % (" ".join([s.path for s in ctx.files.srcs]), destdir),
        "cd %s" % destdir,
        hermetic_npm_binary,
    ])

    # Node/NPM is not (yet) hermeticized, so we have to get the binary
    # from the environment, and it may be under $HOME, so we can't run
    # in the sandbox.
    node_tweaks = dict(
        execution_requirements = {"local": "1"},
        use_default_shell_env = True,
    )
    ctx.actions.run_shell(
        mnemonic = "Bundle",
        inputs = [
            ctx.file._run_npm,
            ctx.file.app,
            ctx.file._bundler_archive,
        ] + list(zips) + ctx.files.srcs,
        outputs = [bundled],
        command = cmd,
        **node_tweaks
    )

    if ctx.attr.split:
        hermetic_npm_command = "export PATH && " + " ".join([
            "python",
            ctx.file._run_npm.path,
            ctx.file._crisper_archive.path,
            "--always-write-script",
            "--source",
            bundled.path,
            "--html",
            ctx.outputs.html.path,
            "--js",
            ctx.outputs.js.path,
        ])

        ctx.actions.run_shell(
            mnemonic = "Crisper",
            inputs = [
                ctx.file._run_npm,
                ctx.file.app,
                ctx.file._crisper_archive,
                bundled,
            ],
            outputs = [ctx.outputs.js, ctx.outputs.html],
            command = hermetic_npm_command,
            **node_tweaks
        )

def _bundle_output_func(name, split):
    _ignore = [name]  # unused.
    out = {"html": "%{name}.html"}
    if split:
        out["js"] = "%{name}.js"
    return out

_bundle_rule = rule(
    _bundle_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = [
            ".js",
            ".html",
            ".txt",
            ".css",
            ".ico",
        ]),
        "app": attr.label(
            mandatory = True,
            allow_single_file = True,
        ),
        "pkg": attr.string(mandatory = True),
        "split": attr.bool(default = True),
        "deps": attr.label_list(providers = [ComponentInfo]),
        "_bundler_archive": attr.label(
            default = Label("@polymer-bundler//:%s" % _npm_tarball("polymer-bundler")),
            allow_single_file = True,
        ),
        "_crisper_archive": attr.label(
            default = Label("@crisper//:%s" % _npm_tarball("crisper")),
            allow_single_file = True,
        ),
        "_run_npm": attr.label(
            default = Label("//tools/js:run_npm_binary.py"),
            allow_single_file = True,
        ),
    },
    outputs = _bundle_output_func,
)

def bundle_assets(*args, **kwargs):
    """Combine html, js, css files and optionally split into js and html bundles."""
    _bundle_rule(pkg = native.package_name(), *args, **kwargs)

def polygerrit_plugin(name, app, srcs = [], deps = [], externs = [], assets = None, plugin_name = None, **kwargs):
    """Bundles plugin dependencies for deployment.

    This rule bundles all Polymer elements and JS dependencies into .html and .js files.
    Run-time dependencies (e.g. JS libraries loaded after plugin starts) should be provided using "assets" property.
    Output of this rule is a FileSet with "${name}_fs", with deploy artifacts in "plugins/${name}/static".

    Args:
      name: String, rule name.
      app: String, the main or root source file.
      externs: Fileset, external definitions that should not be bundled.
      assets: Fileset, additional files to be used by plugin in runtime, exported to "plugins/${name}/static".
      plugin_name: String, plugin name. ${name} is used if not provided.
    """
    if not plugin_name:
        plugin_name = name

    html_plugin = app.endswith(".html")
    srcs = srcs if app in srcs else srcs + [app]

    if html_plugin:
        # Combines all .js and .html files into foo_combined.js and foo_combined.html
        _bundle_rule(
            name = name + "_combined",
            app = app,
            srcs = srcs,
            deps = deps,
            pkg = native.package_name(),
            **kwargs
        )
        js_srcs = [name + "_combined.js"]
    else:
        js_srcs = srcs

    closure_js_library(
        name = name + "_closure_lib",
        srcs = js_srcs + externs,
        convention = "GOOGLE",
        no_closure_library = True,
        deps = [
            "//lib/polymer_externs:polymer_closure",
            "//polygerrit-ui/app/externs:plugin",
        ],
    )

    closure_js_binary(
        name = name + "_bin",
        compilation_level = "WHITESPACE_ONLY",
        defs = [
            "--polymer_version=2",
            "--language_out=ECMASCRIPT_2017",
            "--rewrite_polyfills=false",
        ],
        deps = [
            name + "_closure_lib",
        ],
    )

    if html_plugin:
        native.genrule(
            name = name + "_rename_html",
            srcs = [name + "_combined.html"],
            outs = [plugin_name + ".html"],
            cmd = "sed 's/<script src=\"" + name + "_combined.js\"/<script src=\"" + plugin_name + ".js\"/g' $(SRCS) > $(OUTS)",
            output_to_bindir = True,
        )

    native.genrule(
        name = name + "_rename_js",
        srcs = [name + "_bin.js"],
        outs = [plugin_name + ".js"],
        cmd = "cp $< $@",
        output_to_bindir = True,
    )

    if html_plugin:
        static_files = [plugin_name + ".js", plugin_name + ".html"]
    else:
        static_files = [plugin_name + ".js"]

    if assets:
        nested, direct = [], []
        for x in assets:
            target = nested if "/" in x else direct
            target.append(x)

        static_files += direct

        if nested:
            native.genrule(
                name = name + "_copy_assets",
                srcs = assets,
                outs = [f.split("/")[-1] for f in nested],
                cmd = "cp $(SRCS) $(@D)",
                output_to_bindir = True,
            )
            static_files += [":" + name + "_copy_assets"]

    native.filegroup(
        name = name,
        srcs = static_files,
    )
