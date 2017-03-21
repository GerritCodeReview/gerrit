

def _java_format_test(ctx):
  source_jars = []
  for l in ctx.attr.libs:
    for j in l.java.transitive_source_jars:
      if j.short_path.startswith("..") or j.short_path.startswith("lib/"):
        continue
      source_jars.append(j)

  source_jars = sorted(source_jars)

  gjf = ctx.expand_location("$(location //tools/bzl:google-java-format)",
                            [ctx.attr._binary]
  )
  ctx.file_action(
      output = ctx.outputs.executable,
      content = ('''
#!/bin/sh

set -eu

found_diff="0"

tmp=$(mktemp -d /tmp/gjf.XXXXXX)
mkdir ${tmp}/{before,after}
GJF=%s
for j in %s
do
  unzip -Z1 ${j} | grep '\.java$' > ${tmp}/FILES
  unzip -qd ${tmp}/after ${j}
  unzip -qd ${tmp}/before ${j}

  (cd ${tmp}/after && ${GJF} -i $(cat ../FILES)) &
done
wait

cd ${tmp}

# this must be the last line.
diff -urN before after
''') % (gjf, ' '.join([j.path for j in source_jars])))
  runfiles=ctx.runfiles(
        collect_data=True,
        files = ctx.files._binary)

  return struct(runfiles=runfiles)

java_format_test = rule(
    _java_format_test,
    test = True,
    executable = True,
    attrs = {
        "libs": attr.label_list(allow_files = False),
        "_binary": attr.label(
            default = Label("//tools/bzl:google-java-format"),
            allow_files = True,
        ),
        "_jdk": attr.label(
            default = Label("@local_jdk//:jdk-default"),
            allow_files = True,
        ),
    })
