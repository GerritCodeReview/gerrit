# Copyright (C) 2020 The Android Open Source Project
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
    generated_files = _get_generated_files(outdir, srcs)
    params_file(
        name = name + "_params",
        out = name + ".params",
        args = ["$(execpath {})".format(src) for src in srcs],
        data = srcs,
    )

    args = [
        "echo",
        "$(location //tools/node_tools:template-checker-bin)",
        "--tsconfig $(location {})".format(tsconfig),
        "--out-dir $(RULEDIR)/{} ".format(outdir),
        "--files $(location {})".format(name + ".params"),
        "--execpath $(execpath {})".format(srcs[0]),
        "--rootpath $(rootpath {})".format(srcs[0]),
        "--location $(location {})".format(srcs[0]),
    ]
    if dev_run:
        args.append("--dev-run")
    if out_tsconfig:
        args.append("--out-ts-config $(location {})".format(out_tsconfig))

    native.genrule(
        name = name + "_npm_bin",
        srcs = srcs + deps + [name + ".params"],
        outs = generated_files + ([out_tsconfig] if out_tsconfig else []),
        cmd = " ".join(args) + " && " + "cat $(location {})".format(name + ".params") + "&& pwd && exit 1",
        tools = ["//tools/node_tools:template-checker-bin"],
        # Should not run sandboxed.
        tags = [
            "local",
            "manual",
        ],
    )
    return generated_files

def transform_polymer_templates(name, srcs, tsconfig, deps, out_tsconfig):
    generated_files = _generate_transformed_templates(
        name = name,
        srcs = srcs,
        tsconfig = tsconfig,
        deps = deps,
        out_tsconfig = out_tsconfig,
        dev_run = False,
        outdir = name + "_out",
    )
    generated_dev_files = _generate_transformed_templates(
        name = name + "_dev",
        srcs = srcs,
        tsconfig = tsconfig,
        deps = deps,
        dev_run = True,
        outdir = name + "_dev_out",
        out_tsconfig = None,
    )

    pkg_tar(
        name = name + "_tar",
        srcs = generated_dev_files,
        # Set strip_prefix to keep directory hierarchy in the .tar
        # https://github.com/bazelbuild/rules_pkg/issues/82
        strip_prefix = name + "_dev_out",
    )
    return generated_files
