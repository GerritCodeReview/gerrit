# Utility rule for IDE integration (Eclipse, IntelliJ, etc.).
#
# This rule produces metadata files:
#
#   - %{name}.runtime_classpath
#       One runtime jar path per line. Used to construct the IDE classpath.
#
#   - %{name}.source_classpath
#       One source-jar path per line. Used to attach sources to libraries in
#       the IDE.
#
#   - %{name}.processor_classpath
#       One annotation-processor jar path per line. Used to construct Eclipse .factorypath.
#
# Important implementation details:
#
# * With rules_jvm_external, many Maven artifacts (including sources) are
#   resolved lazily. A jar may appear in a provider but not exist on disk
#   unless it is consumed by an action.
#
# * IDEs require real files on disk. Simply listing paths is insufficient.
#
# * To ensure jars are materialized under the Bazel execution root, this rule
#   declares them as inputs to small, no-op actions ("stamp" actions).
#
# * A param file is used for source jars to avoid command-line length limits.
#   Bazel does not automatically expand "@paramfile" in run_shell, so the
#   script explicitly strips the '@' prefix and reads the file.

load("@rules_java//java:defs.bzl", "JavaInfo")

def _classpath_collector_impl(ctx):
    runtime_sets = []
    source_sets = []
    processor_sets = []

    for d in ctx.attr.deps:
        if JavaInfo in d:
            j = d[JavaInfo]

            runtime_sets.append(j.transitive_runtime_jars)

            ci = j.compilation_info
            if ci and hasattr(ci, "runtime_classpath"):
                runtime_sets.append(ci.runtime_classpath)

            source_sets.append(j.transitive_source_jars)

            ap = j.annotation_processing
            if ap and hasattr(ap, "processor_classpath"):
                processor_sets.append(ap.processor_classpath)

        elif hasattr(d, "files"):
            runtime_sets.append(d.files)

    runtime_files = depset(transitive = runtime_sets).to_list()
    source_files = depset(transitive = source_sets).to_list()
    processor_files = depset(transitive = processor_sets).to_list()

    # Runtime classpath: metadata file.
    ctx.actions.write(
        output = ctx.outputs.runtime,
        content = "\n".join(sorted([f.path for f in runtime_files])),
    )

    # Force runtime jars to be present on disk.
    runtime_stamp = ctx.actions.declare_file(ctx.label.name + ".runtime_materialized")
    ctx.actions.run_shell(
        inputs = runtime_files,
        outputs = [runtime_stamp],
        arguments = [runtime_stamp.path],
        command = r"""
set -euo pipefail
: > "$1"
""",
    )

    # Source classpath: write stable sorted list, and materialize jars by
    # declaring them as inputs.
    pf = ctx.actions.args()
    pf.set_param_file_format("multiline")
    pf.use_param_file("%s", use_always = True)
    pf.add_all([f.path for f in source_files])

    ctx.actions.run_shell(
        inputs = source_files,
        outputs = [ctx.outputs.sources],
        arguments = [ctx.outputs.sources.path, pf],
        command = r"""
set -euo pipefail
OUT="$1"
PF="$2"
PF="${PF#@}"
if [ -n "$PF" ] && [ -f "$PF" ]; then
  sort "$PF" > "$OUT"
else
  : > "$OUT"
fi
""",
    )

    # Processor classpath: metadata file.
    ctx.actions.write(
        output = ctx.outputs.processors,
        content = "\n".join(sorted([f.path for f in processor_files])),
    )

    # Force processor jars to be present on disk.
    processor_stamp = ctx.actions.declare_file(ctx.label.name + ".processors_materialized")
    ctx.actions.run_shell(
        inputs = processor_files,
        outputs = [processor_stamp],
        arguments = [processor_stamp.path],
        command = r"""
set -euo pipefail
: > "$1"
""",
    )

classpath_collector = rule(
    implementation = _classpath_collector_impl,
    attrs = {
        "deps": attr.label_list(),
    },
    outputs = {
        "runtime": "%{name}.runtime_classpath",
        "sources": "%{name}.source_classpath",
        "processors": "%{name}.processor_classpath",
    },
)
