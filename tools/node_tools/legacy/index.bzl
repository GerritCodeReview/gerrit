def _polymer_bundler_tool_impl(ctx):
    html_bundled_file = ctx.actions.declare_file(ctx.label.name + "_tmp.html")
    ctx.actions.run(
        executable = ctx.executable._bundler,
        outputs = [html_bundled_file],
        inputs = ctx.files.srcs,
        arguments = [
            "--inline-css",
            "--sourcemaps",
            "--strip-comments",
            "--root", ctx.file.entry_point.dirname,
            "--out-file", html_bundled_file.path,
            "--in-file", ctx.file.entry_point.basename],
    )

    ctx.actions.run(
        executable = ctx.executable._crisper,
        outputs = [ctx.outputs.html, ctx.outputs.js],
        inputs = [html_bundled_file],
        arguments = ["-s", html_bundled_file.path, "-h", ctx.outputs.html.path, "-j", ctx.outputs.js.path, "--always-write-script", "--script-in-head=false"]
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
        "_crisper": attr.label(
            default = ":crisper-bin",
            executable = True,
            cfg = "host",
        ),
    },
    outputs = {
        "html": "%{name}.html",
        "js": "%{name}.js"
    },
)

