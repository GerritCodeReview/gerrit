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

def _java_nullness_test_impl(ctx):
  deps = set(ctx.files.deps)
  for dep in ctx.attr.deps:
    if hasattr(dep, 'java'):
      deps = deps | dep.java.transitive_runtime_deps
  ctx.template_action(
      template = ctx.file._params_template,
      output = ctx.outputs._params,
      substitutions = {
          "%{jdk}": ctx.file._jdk8.short_path,
          "%{processorpath}": ":".join(
              [ctx.file._checker.short_path, ctx.file._annotation]),
          "%{classpath}": ":".join(
              [dep.short_path for dep in deps] +
              [ctx.file._checker.short_path, ctx.file._annotation]),
          "%{srcs}": "\n".join([f.short_path for f in ctx.files.srcs]),
      })
  ctx.template_action(
      template = ctx.file._command_template,
      output = ctx.outputs.executable,
      executable = True,
      substitutions = {
          "%{javac}": ctx.file._javac.short_path,
          "%{params}": ctx.outputs._params.short_path,
      })
  return struct(
      runfiles = ctx.runfiles(
          files = ctx.files.srcs + list(deps) + [
              ctx.outputs._params,
              ctx.file._jdk8,
              ctx.file._checker,
              ctx.file._javac]))

_java_nullness_test = rule(
    attrs = {
        "_command_template": attr.label(
            default = Label("//tools/bzl:checker_command.txt"),
            allow_single_file = True,
        ),
        "_params_template": attr.label(
            default = Label("//tools/bzl:checker_parameters.txt"),
            allow_single_file = True,
        ),
        "_jdk8": attr.label(
            default = Label("@checker_jdk8//jar"),
            allow_single_file = True,
        ),
        "_checker": attr.label(
            default = Label("@checker_checker//jar"),
            allow_single_file = True,
        ),
        "_annotation": attr.label(
            default = Label("@checker_annotation//jar"),
            allow_single_file = True,
        ),
        "_javac": attr.label(
            default = Label("@bazel_tools//tools/jdk:javac"),
            allow_single_file = True,
        ),
        "srcs": attr.label_list(
            allow_files = True,
        ),
        "deps": attr.label_list(),
    },
    outputs = {
        "_params": "%{name}_params",
    },
    test = True,
    implementation = _java_nullness_test_impl,
)

def java_nullness_test(name, srcs, deps):
  d = {}
  for src in srcs:
    key = src.rpartition('/')[0]
    d[key] = d.setdefault(key, []) + [src]
  tests = []
  for key, srcs in d.items():
    testname = name + '/' + key + '_nullness'
    tests += [testname]
    _java_nullness_test(
        name = testname,
        srcs = srcs,
        deps = deps,
    )
  native.test_suite(
      name = name,
      tests = tests,
  )

def java_library_with_nullness_test(name, srcs, deps, **kwargs):
  native.java_library(
      name = name,
      srcs = srcs,
      deps = deps,
      **kwargs
  )
  java_nullness_test(
      name = name + "_nullness",
      srcs = srcs,
      deps = deps + [name],
  )
