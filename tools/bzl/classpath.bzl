def _classpath_collector(ctx):
    all = depset()
    for d in ctx.attr.deps:
        if hasattr(d, "java"):
            all += d.java.transitive_runtime_deps
            all += d.java.compilation_info.runtime_classpath
        elif hasattr(d, "files"):
            all += d.files

    as_strs = [c.path for c in all.to_list()]
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
