def _symlink_directory_impl(ctx):
    output_dir = ctx.actions.declare_directory(ctx.attr.out_dir)
    input_files = ctx.files.srcs
    strip_prefix = ctx.attr.strip_prefix

    commands = []
    for f in input_files:
        print("Processing file: %s" % f.short_path)
        if strip_prefix and f.short_path.startswith(strip_prefix):
            dest_path = f.short_path[len(strip_prefix):].lstrip("/")
        else:
            dest_path = f.short_path

        link_target = "%s/%s" % (output_dir.path, dest_path)
        rel_prefix = link_target.count("/") * "../"
        commands.append("mkdir -pv '%s'" % (link_target))
        commands.append("ln -sFv '%s' '%s'" % (rel_prefix + f.path, link_target))

    ctx.actions.run_shell(
      inputs = input_files,
      outputs = [output_dir],
      command = " && ".join(commands),
      progress_message = "Symlinking directory %s" % ctx.label,
    )

    return [DefaultInfo(files = depset([output_dir]))]

symlink_directory = rule(
    implementation = _symlink_directory_impl,
    attrs = {
        "srcs": attr.label_list(
            doc = "The files to include in the directory.",
            allow_files = True,
            mandatory = True,
        ),
        "strip_prefix": attr.string(
            doc = "Prefix to strip from the input files' paths.",
            default = "",
        ),
        "out_dir": attr.string(
            doc = "The output directory name.",
            mandatory = True,
        ),
    },
    doc = "Creates a directory of symlinks to a set of files, preserving structure.",
)
