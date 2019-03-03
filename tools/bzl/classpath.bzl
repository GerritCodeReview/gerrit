def _classpath_collector(ctx):
    all = []
    for d in ctx.attr.deps:
        if hasattr(d, "java"):
            all.append(d.java.transitive_runtime_deps)
            if hasattr(d.java.compilation_info, "runtime_classpath"):
                all.append(d.java.compilation_info.runtime_classpath)
        elif hasattr(d, "files"):
            all.append(d.files)

    as_strs = [c.path for c in depset(transitive = all).to_list()]
    ctx.actions.write(
        output = ctx.outputs.runtime,
        content = "\n".join(sorted(as_strs)),
    )

classpath_collector = rule(
    attrs = {
        "deps": attr.label_list(),
    },
    outputs = {
        "runtime": "%{name}.runtime_classpath",
    },
    implementation = _classpath_collector,
)
