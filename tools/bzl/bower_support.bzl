#TODO: remove this file after plugins migration to npm
load(":js.bzl", "ComponentInfo")

_common_attrs = {
    "deps": attr.label_list(providers = [ComponentInfo]),
}

NPM_VERSIONS = {
    "bower": "1.8.8",
    "crisper": "2.0.2",
    "polymer-bundler": "4.0.9",
}
NPM_SHA1S = {
    "bower": "82544be34a33aeae7efb8bdf9905247b2cffa985",
    "crisper": "7183c58cea33632fb036c91cefd1b43e390d22a2",
    "polymer-bundler": "c80c9815690d76656d1fa6a231481850b4fa3874",
}

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
