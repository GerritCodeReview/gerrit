def _update_links_impl(ctx):
    dir_name = ctx.label.name
    output_files = []
    input_js_files = []
    output_js_files = []
    js_files_args = ctx.actions.args()
    js_files_args.set_param_file_format("multiline")
    js_files_args.use_param_file("%s", use_always = True)

    for f in ctx.files.srcs:
        output_file = ctx.actions.declare_file(dir_name + "/" + f.path)
        output_files.append(output_file)
        if f.extension == "html":
            input_js_files.append(f)
            output_js_files.append(output_file)
            js_files_args.add(f)
            js_files_args.add(output_file)
        else:
            ctx.actions.expand_template(
                output = output_file,
                template = f,
                substitutions = {},
            )

    ctx.actions.run(
        executable = ctx.executable._updater,
        outputs = output_js_files,
        inputs = input_js_files + [ctx.file.redirects],
        arguments = [js_files_args, ctx.file.redirects.path],
    )
    return [DefaultInfo(files = depset(output_files))]

update_links = rule(
    implementation = _update_links_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "redirects": attr.label(allow_single_file = True, mandatory = True),
        "_updater": attr.label(
            default = ":links-updater-bin",
            executable = True,
            cfg = "host",
        ),
    },
)

def additional_root_paths(ctx):
    return ctx.attr.additional_root_paths + [
        # also add additional_root_paths variants from genfiles dir and bin dir
        "/".join([ctx.genfiles_dir.path, p])
        for p in ctx.attr.additional_root_paths
    ] + [
        "/".join([ctx.bin_dir.path, p])
        for p in ctx.attr.additional_root_paths
    ] + [
        # package path is the root, including in bin/gen
        ctx.label.package,
        "/".join([ctx.bin_dir.path, ctx.label.package]),
        "/".join([ctx.genfiles_dir.path, ctx.label.package]),

        # bazel-bin/gen dirs to absolute paths
        ctx.genfiles_dir.path,
        ctx.bin_dir.path,

        # package re-rooted subdirectory
        "/".join([p for p in [ctx.bin_dir.path, ctx.label.package, "_" + ctx.label.name, ctx.label.package] if p]),
    ]

def _impl(ctx):
    root_paths = additional_root_paths(ctx)

    print(root_paths)
    #print(ctx.files.srcs)

_DOC = """Assembles a web application from source files."""

_ATTRS = {
    "srcs": attr.label_list(
        allow_files = True,
        doc = """Files which should be copied into the package""",
    ),
    "additional_root_paths": attr.string_list(
        doc = """Path prefixes to strip off all srcs, in addition to the current package. Longest wins.""",
    ),
    "_assembler": attr.label(
        default = "@build_bazel_rules_nodejs//internal/pkg_web:assembler",
        executable = True,
        cfg = "host",
    ),
}

pkg_web3 = rule(
    implementation = _impl,
    attrs = _ATTRS,
    doc = _DOC,
)

def pkg_web2(name, srcs, additional_root_paths):
    pkg_web3(
        name = name,
        srcs = srcs,
        additional_root_paths = additional_root_paths + [native.package_name() + "/polygerrit-ui-updated-links"],
    )
    print(type(Label("//abc")))
    for r in additional_root_paths:
        print(additional_root_paths)
        print(type(r))
