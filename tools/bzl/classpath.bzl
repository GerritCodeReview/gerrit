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
# * To ensure jars are materialized under the Bazel execution root, the
#   actions producing the metadata files declare them as inputs and validate
#   that they exist.
#
# * A param file is used for source jars to avoid command-line length limits.
#   Bazel does not automatically expand "@paramfile" in run_shell, so the
#   script explicitly strips the '@' prefix and reads the file.

"""Utility rule for IDE integration."""

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

    # Runtime classpath: write stable sorted list, and materialize jars by
    # declaring them as inputs.
    pf = ctx.actions.args()
    pf.set_param_file_format("multiline")
    pf.use_param_file("%s", use_always = True)
    pf.add_all([f.path for f in runtime_files])

    ctx.actions.run_shell(
        inputs = runtime_files,
        outputs = [ctx.outputs.runtime],
        mnemonic = "ClasspathCollector",
        arguments = [ctx.outputs.runtime.path, pf],
        command = r"""
set -euo pipefail
OUT="$1"
PF="$2"
PF="${PF#@}"
if [ -n "$PF" ] && [ -f "$PF" ]; then
  while IFS= read -r f; do
    test -e "$f"
  done < "$PF"
  sort "$PF" > "$OUT"
else
  : > "$OUT"
fi
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
        mnemonic = "ClasspathCollector",
        arguments = [ctx.outputs.sources.path, pf],
        command = r"""
set -euo pipefail
OUT="$1"
PF="$2"
PF="${PF#@}"
if [ -n "$PF" ] && [ -f "$PF" ]; then
  while IFS= read -r f; do
    test -e "$f"
  done < "$PF"
  sort "$PF" > "$OUT"
else
  : > "$OUT"
fi
""",
    )

    # Processor classpath: write stable sorted list, and materialize jars by
    # declaring them as inputs.
    pf = ctx.actions.args()
    pf.set_param_file_format("multiline")
    pf.use_param_file("%s", use_always = True)
    pf.add_all([f.path for f in processor_files])

    ctx.actions.run_shell(
        inputs = processor_files,
        outputs = [ctx.outputs.processors],
        mnemonic = "ClasspathCollector",
        arguments = [ctx.outputs.processors.path, pf],
        command = r"""
set -euo pipefail
OUT="$1"
PF="$2"
PF="${PF#@}"
if [ -n "$PF" ] && [ -f "$PF" ]; then
  while IFS= read -r f; do
    test -e "$f"
  done < "$PF"
  sort "$PF" > "$OUT"
else
  : > "$OUT"
fi
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
