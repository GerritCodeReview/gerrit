def _polymer_bundler_tool_impl(ctx):
    ctx.actions.run(
        executable = ctx.executable._bundler,
        outputs = [ctx.outputs.html],
        inputs = ctx.files.srcs,
        arguments = ["--root", ctx.file.entry_point.dirname, "--out-file", ctx.outputs.html.path, "--in-file", ctx.file.entry_point.basename],
    )

polymer_bundler_tool = rule(
    implementation = _polymer_bundler_tool_impl,
    attrs = {
        "entry_point": attr.label(allow_single_file = True, mandatory = True),
        "srcs": attr.label_list(allow_files = True),
        "_bundler": attr.label(
            default = ":polymer-bundler-bin",
            executable = True,
            cfg = "host",
        ),
    },
    outputs = {
        "html": "%{name}.html",
    },
)
