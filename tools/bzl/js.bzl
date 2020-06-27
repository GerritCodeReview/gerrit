load("@npm//@bazel/terser:index.bzl", "terser_minified")
load("//lib/js:npm.bzl", "NPM_SHA1S", "NPM_VERSIONS")

NPMJS = "NPMJS"

GERRIT = "GERRIT:"

def _npm_tarball(name):
    return "%s@%s.npm_binary.tgz" % (name, NPM_VERSIONS[name])

def _npm_binary_impl(ctx):
    """rule to download a NPM archive."""
    name = ctx.name
    version = NPM_VERSIONS[name]
    sha1 = NPM_SHA1S[name]

    dir = "%s-%s" % (name, version)
    filename = "%s.tgz" % dir
    base = "%s@%s.npm_binary.tgz" % (name, version)
    dest = ctx.path(base)
    repository = ctx.attr.repository
    if repository == GERRIT:
        url = "https://gerrit-maven.storage.googleapis.com/npm-packages/%s" % filename
    elif repository == NPMJS:
        url = "https://registry.npmjs.org/%s/-/%s" % (name, filename)
    else:
        fail("repository %s not in {%s,%s}" % (repository, GERRIT, NPMJS))

    python = ctx.which("python")
    script = ctx.path(ctx.attr._download_script)

    args = [python, script, "-o", dest, "-u", url, "-v", sha1]
    out = ctx.execute(args)
    if out.return_code:
        fail("failed %s: %s" % (args, out.stderr))
    ctx.file("BUILD", "package(default_visibility=['//visibility:public'])\nfilegroup(name='tarball', srcs=['%s'])" % base, False)

npm_binary = repository_rule(
    attrs = {
        "repository": attr.string(default = NPMJS),
        # Label resolves within repo of the .bzl file.
        "_download_script": attr.label(default = Label("//tools:download_file.py")),
    },
    local = True,
    implementation = _npm_binary_impl,
)

ComponentInfo = provider()

# for use in repo rules.
def _run_npm_binary_str(ctx, tarball, args):
    python_bin = ctx.which("python")
    return " ".join([
        str(python_bin),
        str(ctx.path(ctx.attr._run_npm)),
        str(ctx.path(tarball)),
    ] + args)

def _bower_archive(ctx):
    """Download a bower package."""
    download_name = "%s__download_bower.zip" % ctx.name
    renamed_name = "%s__renamed.zip" % ctx.name
    version_name = "%s__version.json" % ctx.name

    cmd = [
        ctx.which("python"),
        ctx.path(ctx.attr._download_bower),
        "-b",
        "%s" % _run_npm_binary_str(ctx, ctx.attr._bower_archive, []),
        "-n",
        ctx.name,
        "-p",
        ctx.attr.package,
        "-v",
        ctx.attr.version,
        "-s",
        ctx.attr.sha1,
        "-o",
        download_name,
    ]

    out = ctx.execute(cmd)
    if out.return_code:
        fail("failed %s: %s" % (cmd, out.stderr))

    _bash(ctx, " && ".join([
        "TMP=$(mktemp -d || mktemp -d -t bazel-tmp)",
        "TZ=UTC",
        "export UTC",
        "cd $TMP",
        "mkdir bower_components",
        "cd bower_components",
        "unzip %s" % ctx.path(download_name),
        "cd ..",
        "find . -exec touch -t 198001010000 '{}' ';'",
        "zip -Xr %s bower_components" % renamed_name,
        "cd ..",
        "rm -rf ${TMP}",
    ]))

    dep_version = ctx.attr.semver if ctx.attr.semver else ctx.attr.version
    ctx.file(
        version_name,
        '"%s":"%s#%s"' % (ctx.name, ctx.attr.package, dep_version),
    )
    ctx.file(
        "BUILD",
        "\n".join([
            "package(default_visibility=['//visibility:public'])",
            "filegroup(name = 'zipfile', srcs = ['%s'], )" % download_name,
            "filegroup(name = 'version_json', srcs = ['%s'], visibility=['//visibility:public'])" % version_name,
        ]),
        False,
    )

def _bash(ctx, cmd):
    cmd_list = ["bash", "-c", cmd]
    out = ctx.execute(cmd_list)
    if out.return_code:
        fail("failed %s: %s" % (cmd_list, out.stderr))

bower_archive = repository_rule(
    _bower_archive,
    attrs = {
        "package": attr.string(mandatory = True),
        "semver": attr.string(),
        "sha1": attr.string(mandatory = True),
        "version": attr.string(mandatory = True),
        "_bower_archive": attr.label(default = Label("@bower//:%s" % _npm_tarball("bower"))),
        "_download_bower": attr.label(default = Label("//tools/js:download_bower.py")),
        "_run_npm": attr.label(default = Label("//tools/js:run_npm_binary.py")),
    },
)

def _bower_component_impl(ctx):
    transitive_zipfiles = depset(
        direct = [ctx.file.zipfile],
        transitive = [d[ComponentInfo].transitive_zipfiles for d in ctx.attr.deps],
    )

    transitive_licenses = depset(
        direct = [ctx.file.license],
        transitive = [d[ComponentInfo].transitive_licenses for d in ctx.attr.deps],
    )

    transitive_versions = depset(
        direct = ctx.files.version_json,
        transitive = [d[ComponentInfo].transitive_versions for d in ctx.attr.deps],
    )

    return [
        ComponentInfo(
            transitive_licenses = transitive_licenses,
            transitive_versions = transitive_versions,
            transitive_zipfiles = transitive_zipfiles,
        ),
    ]

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

_bower_component = rule(
    _bower_component_impl,
    attrs = dict(_common_attrs.items() + {
        "license": attr.label(allow_single_file = True),

        # If set, define by hand, and don't regenerate this entry in bower2bazel.
        "seed": attr.bool(default = False),
        "version_json": attr.label(allow_files = [".json"]),
        "zipfile": attr.label(allow_single_file = [".zip"]),
    }.items()),
)

# TODO(hanwen): make license mandatory.
def bower_component(name, license = None, **kwargs):
    prefix = "//lib:LICENSE-"
    if license and not license.startswith(prefix):
        license = prefix + license
    _bower_component(
        name = name,
        license = license,
        zipfile = "@%s//:zipfile" % name,
        version_json = "@%s//:version_json" % name,
        **kwargs
    )

def _bower_component_bundle_impl(ctx):
    """A bunch of bower components zipped up."""
    zips = depset()
    for d in ctx.attr.deps:
        files = d[ComponentInfo].transitive_zipfiles

        # TODO(davido): Make sure the field always contains a depset
        if type(files) == "list":
            files = depset(files)
        zips = depset(transitive = [zips, files])

    versions = depset(transitive = [d[ComponentInfo].transitive_versions for d in ctx.attr.deps])

    licenses = depset(transitive = [d[ComponentInfo].transitive_versions for d in ctx.attr.deps])

    out_zip = ctx.outputs.zip
    out_versions = ctx.outputs.version_json

    ctx.actions.run_shell(
        inputs = zips.to_list(),
        outputs = [out_zip],
        command = " && ".join([
            "p=$PWD",
            "TZ=UTC",
            "export TZ",
            "rm -rf %s.dir" % out_zip.path,
            "mkdir -p %s.dir/bower_components" % out_zip.path,
            "cd %s.dir/bower_components" % out_zip.path,
            "for z in %s; do unzip -q $p/$z ; done" % " ".join(sorted([z.path for z in zips.to_list()])),
            "cd ..",
            "find . -exec touch -t 198001010000 '{}' ';'",
            "zip -Xqr $p/%s bower_components/*" % out_zip.path,
        ]),
        mnemonic = "BowerCombine",
    )

    ctx.actions.run_shell(
        inputs = versions.to_list(),
        outputs = [out_versions],
        mnemonic = "BowerVersions",
        command = "(echo '{' ; for j in  %s ; do cat $j; echo ',' ; done ; echo \\\"\\\":\\\"\\\"; echo '}') > %s" % (" ".join([v.path for v in versions.to_list()]), out_versions.path),
    )

    return [
        ComponentInfo(
            transitive_licenses = licenses,
            transitive_versions = versions,
            transitive_zipfiles = zips,
        ),
    ]

bower_component_bundle = rule(
    _bower_component_bundle_impl,
    attrs = _common_attrs,
    outputs = {
        "version_json": "%{name}-versions.json",
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
        "--sourcemaps",
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
            "--script-in-head=false",
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

def polygerrit_plugin(name, app, srcs = [], deps = [], assets = None, plugin_name = None, **kwargs):
    """Bundles plugin dependencies for deployment.

    This rule bundles all Polymer elements and JS dependencies into .html and .js files.
    Run-time dependencies (e.g. JS libraries loaded after plugin starts) should be provided using "assets" property.
    Output of this rule is a FileSet with "${name}_fs", with deploy artifacts in "plugins/${name}/static".

    Args:
      name: String, rule name.
      app: String, the main or root source file.
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

    native.filegroup(
        name = name + "-src-fg",
        srcs = js_srcs,
    )

    terser_minified(
        name = name + ".min",
        sourcemap = False,
        src = name + "-src-fg",
    )

    if html_plugin:
        native.genrule(
            name = name + "_rename_html",
            srcs = [name + "_combined.html"],
            outs = [plugin_name + ".html"],
            cmd = "sed 's/<script src=\"" + name + "_combined.js\"/<script src=\"" + plugin_name + ".js\"/g' $(SRCS) > $(OUTS)",
            output_to_bindir = True,
        )
    else:
        # For polymer 3 migration, we will only have js plugins, in case server side
        # is still asking for *.html, we still want to create a html placeholder just to load the js
        # TODO(taoalpha): this should be cleaned up once polymer 3 plugins are the only ones gerrit supports
        native.genrule(
            name = name + "_rename_html",
            outs = [plugin_name + ".html"],
            cmd = "echo \"<script src='" + plugin_name + ".js'></script>\" > $(OUTS)",
            output_to_bindir = True,
        )

    native.genrule(
        name = name + "_rename_js",
        srcs = [name + ".min"],
        outs = [plugin_name + ".js"],
        cmd = "cp $< $@",
        output_to_bindir = True,
    )

    static_files = [plugin_name + ".js", plugin_name + ".html"]

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
            static_files.append(":" + name + "_copy_assets")

    native.filegroup(
        name = name,
        srcs = static_files,
    )
