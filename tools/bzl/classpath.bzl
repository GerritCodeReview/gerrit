
def _classpath_collector(ctx):
    all = set()
    for d in ctx.attr.deps:
        all += d.java.compilation_info.runtime_classpath

    as_strs = [c.path for c in all]
    ctx.file_action(output= ctx.outputs.runtime,
                    content="\n".join(sorted(as_strs)))


classpath_collector = rule(
    implementation = _classpath_collector,
    attrs = {
        "deps": attr.label_list(),
    },
    outputs={
        "runtime": "%{name}.runtime_classpath"
    })
