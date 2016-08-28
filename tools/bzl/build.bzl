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

# War packaging.

def _impl(ctx):
  war_output = ctx.outputs.war
  tmp = ctx.outputs.war.path + ".tmp"

  transitive_lib_set = set([])
  for l in ctx.attr.libs:
    transitive_lib_set += l.java.transitive_deps
  transitive_lib_paths = [j.path for j in list(transitive_lib_set)]

  transitive_pgmlib_set = set([])
  for l in ctx.attr.pgmlibs:
    transitive_pgmlib_set += l.java.transitive_runtime_deps
  transitive_pgmlib_paths = [j.path for j in list(transitive_pgmlib_set)]

  args = ['-o', war_output.path, '--tmp', tmp]

  for p in transitive_lib_paths:
    args.extend(['--lib', p])
  for p in transitive_pgmlib_paths:
    args.extend(['--pgmlib', p])

  # TODO:(davido) Find out how to access path of genrule or java_binary
#  if ctx.attr.context:
#    for t in ctx.attr.context:
#      print(dir(t.label))
#      args.append(t)

  ctx.action(
      executable = ctx.executable._runner,
      arguments = args,
      outputs = [war_output],
  )

war = rule(
    attrs = {
      "context": attr.label_list(allow_files = True),
      "libs": attr.label_list(allow_files = False),
      "pgmlibs": attr.label_list(allow_files = False),
      "_runner": attr.label(allow_files=True, executable=True, default=Label("//tools/bzl:pack_war")),
    },
    implementation = _impl,
    outputs = {"war" : "%{name}.war"},
)
