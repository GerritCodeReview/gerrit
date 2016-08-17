# -*- python -*-

# Javadoc rule.

def _impl(ctx):
  zip_output = ctx.outputs.zip

  classpath = []
  transitive_jar_set = set([])
  source_jars = []
  for l in ctx.attr.libs:
    source_jars += list(l.java.source_jars)
    classpath.append(l.java.compilation_info.compilation_classpath)
    transitive_jar_set += l.java.transitive_deps

  transitive_jar_paths = [j.path for j in list(transitive_jar_set)]
  dir = ctx.outputs.zip.path + ".dir"
  cmd = [
      "mkdir %s" % dir,
      " ".join([
        ctx.file._javadoc.path,
        "-protected",
        "-encoding UTF-8",
        "-charset UTF-8",
        "-notimestamp",
        "-quiet",
        "-windowtitle", ctx.attr.title,
        "-link", "http://docs.oracle.com/javase/7/docs/api",
        "-sourcepath", ":".join([j.path for j in source_jars]),
        "-subpackages ",
        ":".join(ctx.attr.pkgs),
        " -classpath ",
        ":".join(transitive_jar_paths),
        "-d %s" % dir]),
    "find %s -exec touch -t 198001010000 '{}' ';'" % dir,
    "(cd %s && zip -r ../%s *)" % (dir, ctx.outputs.zip.basename),
  ]
  ctx.action(
      inputs = list(transitive_jar_set) + source_jars + ctx.files._jdk,
      outputs = [zip_output],
      command = " && ".join(cmd))

java_doc = rule(
    attrs = {
      "libs": attr.label_list(allow_files = False),
      "deps" : attr.label_list(allow_files = False),
      "pkgs": attr.string_list(),
      "title": attr.string(),
      "_javadoc": attr.label(
        default = Label("@local_jdk//:bin/javadoc"),
        single_file = True,
        allow_files = True),
      "_jdk": attr.label(
        default = Label("@local_jdk//:jdk-default"),
        allow_files = True),
    },
    implementation = _impl,
    outputs = {"zip" : "%{name}.zip"},
)
