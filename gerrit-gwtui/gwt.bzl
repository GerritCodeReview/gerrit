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

# Port of Buck native gwt_binary() rule. See discussion in context of
# https://github.com/facebook/buck/issues/109

jar_filetype = FileType(['.jar'])
GWT_COMPILER = "com.google.gwt.dev.Compiler"

def _impl(ctx):
  output_zip = ctx.outputs.output
  output_dir = output_zip.path + '.gwt_output'
  deploy_dir = output_zip.path + '.gwt_deploy'

  deps = _get_transitive_closure(ctx)

  paths = []
  for dep in deps:
    if dep.path.find('ijar.jar') == -1:
      paths.append(dep.path)  
 
  cmd = "external/local_jdk/bin/java %s -Dgwt.normalizeTimestamps=true -cp %s %s -war %s -deploy %s " % (
    " ".join(ctx.attr.jvm_args),
    ":".join(paths),
    GWT_COMPILER,
    output_dir,
    deploy_dir,
  )

  cmd += "-style %s " % ctx.attr.style
  cmd += "-optimize %s " % ctx.attr.optimize
  cmd += "-strict "
  cmd += " ".join(ctx.attr.compiler_args) + " "
  cmd += " ".join(ctx.attr.modules) + "\n"

  cmd += "rm -rf %s/gwt-unitCache\n" % output_dir
  cmd += "root=`pwd`\n"
  cmd += "cd %s; $root/%s Cc ../%s $(find .)\n" % (
      output_dir,
      ctx.executable._zip.path,
      output_zip.basename,
  )

  ctx.action(
    inputs = list(deps) + ctx.files._jdk + ctx.files._zip,
    outputs = [output_zip],
    mnemonic = "GwtBinary",
    progress_message = "GWT compiling " + output_zip.short_path,
    command = "set -e\n" + cmd,
  )

def _get_transitive_closure(ctx):
  deps = set()
  for dep in ctx.attr.module_deps:
    deps += dep.java.transitive_deps
    deps += dep.java.transitive_runtime_deps
  for dep in ctx.attr.deps:
    if hasattr(dep, 'java'):
      deps += dep.java.transitive_deps
      deps += dep.java.transitive_runtime_deps
    elif hasattr(dep, 'files'):
      deps += dep.files

  return deps

gwt_binary = rule(
    implementation = _impl,
    attrs = {
        "style": attr.string(default = "OBF"),
        "optimize": attr.string(default = "9"),
        "deps": attr.label_list(allow_files=jar_filetype),
        "modules": attr.string_list(mandatory=True),
        "module_deps": attr.label_list(allow_files=jar_filetype),
        "compiler_args": attr.string_list(),
        "jvm_args": attr.string_list(),
        "_jdk": attr.label(
            default=Label("//tools/defaults:jdk")),
        "_zip": attr.label(
            default=Label("@bazel_tools//tools/zip:zipper"),
            executable=True,
            single_file=True),
    },
    outputs = {
        "output": "%{name}.zip",
    },
  )
