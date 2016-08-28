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

  inputs = []

  transitive_lib_deps = set()
  transitive_lib_paths = []
  for l in ctx.attr.libs:
    transitive_lib_deps += l.java.transitive_deps
    transitive_lib_deps += l.java.transitive_runtime_deps
  for dep in transitive_lib_deps:
    inputs.append(dep)
    transitive_lib_paths.append(dep.path)

  transitive_pgmlib_deps = set()
  transitive_pgmlib_paths = []
  for l in ctx.attr.pgmlibs:
    transitive_pgmlib_deps += l.java.transitive_deps
    transitive_pgmlib_deps += l.java.transitive_runtime_deps
  for dep in transitive_pgmlib_deps:
    inputs.append(dep)
    transitive_pgmlib_paths.append(dep.path)

  args = ['-o', war_output.path, '--tmp', tmp]

  for p in transitive_lib_paths:
    args.extend(['--lib', p])
  for p in transitive_pgmlib_paths:
    args.extend(['--pgmlib', p])

  transitive_context_deps = set()
  if ctx.attr.context:
    for jar in ctx.attr.context:
      if hasattr(jar, "java"):  # java_library, java_import
        transitive_context_deps += jar.java.transitive_runtime_deps
      elif hasattr(jar, "files"):  # a jar file
        transitive_context_deps += jar.files
  for dep in transitive_context_deps:
    inputs.append(dep)
    args.append(dep.path)

  ctx.action(
    inputs = inputs,
    outputs = [war_output],
    mnemonic = 'WAR',
    executable = ctx.executable._runner,
    arguments = args,
  )

pkg_war = rule(
    attrs = {
      "context": attr.label_list(allow_files = True),
      "libs": attr.label_list(allow_files = False),
      "pgmlibs": attr.label_list(allow_files = False),
      "_runner": attr.label(allow_files=True, executable=True, default=Label("//tools/bzl:pack_war")),
    },
    implementation = _impl,
    outputs = {"war" : "%{name}.war"},
)
