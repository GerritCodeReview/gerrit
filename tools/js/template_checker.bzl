# Copyright (C) 2021 The Android Open Source Project
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

"""This file contains macro to run polymer templates check."""

load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary", "nodejs_test", "npm_package_bin", "params_file")
load("@rules_pkg//:pkg.bzl", "pkg_tar")

def _get_generated_files(outdir, srcs):
    result = []
    for f in srcs:
        result.append(outdir + "/" + f)
    return result

def _generate_transformed_templates(name, srcs, tsconfig, deps, out_tsconfig, outdir, dev_run):
    """Generates typescript code from polymer templates. It uses twinkie package
    for generation.

    Args:
      name: rule name
      srcs: all files in a project project
      tsconfig: the original typescript project file
      deps: dependencies
      out_tsconfig: where to store the generated TS project.
      outdir: where to store generated .ts files
      dev_run: if True, the generator uses different file paths in generated
        import statements. Later, generated files can be copied into workspace
        for future debugging\\investigation templates issues.

    Returns:
      The list of generated files
    """
    generated_files = _get_generated_files(outdir, srcs)

    # There is a limitation on the command-line length. Put all source files
    # into a .params file (this is a text file, where each argument is placed
    # on a new line)
    params_file(
        name = name + "_params",
        out = name + ".params",
        args = ["$(execpath {})".format(src) for src in srcs],
        data = srcs,
    )

    # Arguments for twinkie
    args = [
        "$(location //tools/node_tools:twinkie-bin)",
        "--tsconfig $(location {})".format(tsconfig),
        "--out-dir $(RULEDIR)/{} ".format(outdir),
        "--files $(location {})".format(name + ".params"),
    ]
    if dev_run:
        args.append("--dev-run")
    if out_tsconfig:
        args.append("--out-ts-config $(location {})".format(out_tsconfig))

    # Execute twinkie.
    native.genrule(
        name = name + "_npm_bin",
        srcs = srcs + deps + [name + ".params"],
        outs = generated_files + ([out_tsconfig] if out_tsconfig else []),
        cmd = " ".join(args),
        tools = ["//tools/node_tools:twinkie-bin"],
        # Should not run sandboxed.
        tags = [
            "local",
            "manual",
        ],
    )
    return generated_files

def transform_polymer_templates(name, srcs, tsconfig, deps, out_tsconfig):
    """Transforms polymer templates into typescript code.
    Additionally, the macro defines name+"_tar" package that contains
    generated code with slightly different import paths.
    Note, that polygerrit template tests don't depend on the tar package, so
    bazel doesn't generate the tar package with the bazel test command.
    The tar package must be build explicitly with the bazel build command.

    Args:
      name: rule name
      srcs: all files in a project project
      tsconfig: the original typescript project file
      deps: dependencies
      out_tsconfig: where to store the generated TS project.

    Returns:
      list of generated files
    """

    # Transformed templates for tests
    generated_files = _generate_transformed_templates(
        name = name,
        srcs = srcs,
        tsconfig = tsconfig,
        deps = deps,
        out_tsconfig = out_tsconfig,
        dev_run = False,
        outdir = name + "_out",
    )

    # Transformed templates for developers. Only the tar package depends
    # on it and it never runs during tests.
    generated_dev_files = _generate_transformed_templates(
        name = name + "_dev",
        srcs = srcs,
        tsconfig = tsconfig,
        deps = deps,
        dev_run = True,
        outdir = name + "_dev_out",
        out_tsconfig = None,
    )

    # Pack all transformed files. Later files can be materialized in the
    # WORKSPACE/polygerrit-ui/app/tmpl_out dir.
    pkg_tar(
        name = name + "_tar",
        srcs = generated_dev_files,
        # Set strip_prefix to keep directory hierarchy in the .tar
        # https://github.com/bazelbuild/rules_pkg/issues/82
        strip_prefix = name + "_dev_out",
    )
    return generated_files
