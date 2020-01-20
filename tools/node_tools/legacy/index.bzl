# File contains a wrapper for legacy polymer-bundler and crisper tools.
# File must be removed after get rid of HTML imports

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
            "--root",
            ctx.file.entry_point.dirname,
            "--out-file",
            html_bundled_file.path,
            "--in-file",
            ctx.file.entry_point.basename,
        ],
    )

    output_js_file = ctx.outputs.js
    if ctx.attr.script_src_value:
        output_js_file = ctx.actions.declare_file(ctx.attr.script_src_value, sibling = ctx.outputs.html)
    script_src_value = ctx.attr.script_src_value if ctx.attr.script_src_value else ctx.outputs.js.path

    ctx.actions.run(
        executable = ctx.executable._crisper,
        outputs = [ctx.outputs.html, output_js_file],
        inputs = [html_bundled_file],
        arguments = ["-s", html_bundled_file.path, "-h", ctx.outputs.html.path, "-j", output_js_file.path, "--always-write-script", "--script-in-head=false"],
    )

    if ctx.attr.script_src_value:
        ctx.actions.expand_template(
            template = output_js_file,
            output = ctx.outputs.js,
            substitutions = {},
        )

polymer_bundler_tool = rule(
    implementation = _polymer_bundler_tool_impl,
    attrs = {
        "entry_point": attr.label(allow_single_file = True, mandatory = True),
        "srcs": attr.label_list(allow_files = True),
        "script_src_value": attr.string(),
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
        "js": "%{name}.js",
    },
)
