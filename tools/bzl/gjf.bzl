

def _java_format_test(ctx):
  source_jars = []
  for l in ctx.attr.libs:
    for j in l.java.transitive_source_jars:
      if j.short_path.startswith("..") or j.short_path.startswith("lib/"):
        continue
      source_jars.append(j)

  source_jars = sorted(source_jars)

  jvm = ctx.file._jvm.short_path
  gjf = ctx.file._gjf.short_path

  exc = ' -or '.join(
    ["-name '%s*.java'" % f
     for f in ctx.attr.exceptions[:] + ["AutoValue_", "AutoAnnotate_"]])
  srcs = ' '.join([j.short_path for j in source_jars])
  ctx.file_action(
      output = ctx.outputs.executable,
      content = ('''
#!/bin/sh

set -eu

found_diff="0"

tmp=$(mktemp -d /tmp/gjf.XXXXXX)
mkdir ${tmp}/{before,after}
root=${TEST_SRCDIR}/%s
JAVA=${root}/%s
GJF=${root}/%s
for j in %s
do
  full=${root}/$j

  unzip -oqd ${tmp}/after ${full}
  unzip -oqd ${tmp}/before ${full}
done

find ${tmp}/{before,after} %s | xargs rm -f

(cd ${tmp}/after; find -name  '*.java' | xargs -P10  ${JAVA} -jar ${GJF} -i)

cd ${tmp}

# this must be the last line.
diff -urN before after
''') % (ctx.workspace_name, jvm, gjf, srcs, exc))
  runfiles=ctx.runfiles(
        collect_data=True,
        files = ctx.files._gjf + source_jars + ctx.files._jdk)

  return struct(runfiles=runfiles)

java_format_test = rule(
    _java_format_test,
    test = True,
    executable = True,
    attrs = {
      "exceptions": attr.string_list(),
        "libs": attr.label_list(allow_files = False),
        "_gjf": attr.label(
            default = Label("@google_java_format//jar"),
            allow_files = True,
            single_file = True,
        ),
        "_jdk": attr.label(
            default = Label("@local_jdk//:jdk-default"),
            allow_files = True,
        ),
        "_jvm": attr.label(
            default = Label("@local_jdk//:java"),
            single_file = True,
            allow_files = True,
        ),
    })
