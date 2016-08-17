# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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
