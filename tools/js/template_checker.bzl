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

def transform_polymer_templates(name, srcs, tsconfig, deps):
    # entry_point = "@npm//twinkie/bin:index"
    entry_point = "@npm//:node_modules/twinkie/bin/index.js"
    outdir = name + "_out"
    generated_files = _get_generated_files(outdir, srcs)
    params_file(
        name = name + "_params",
        out = name + ".params",
        args = ["$(execpath {})".format(src) for src in srcs],
        data = srcs,
    )

    nodejs_binary(
        name = name + "_checker_bin",
        # Point bazel to your node_modules to find the entry point
        data = ["@npm//:node_modules"],
        entry_point = entry_point,
        # Should not run sandboxed.
        tags = [
            "local",
            "manual",
        ],
    )

    tsconfig_name = "tsconfig_" + name + ".json"

    native.genrule(
        name = name + "_npm_bin",
        srcs = srcs + deps + [name + ".params"],
        outs = generated_files + [tsconfig_name],
        cmd = " && ".join([
            "$(location {}) $(location {}) $(RULEDIR)/{} $(location {}) $(location {}) --relative".format(name + "_checker_bin", tsconfig, outdir, name + ".params", tsconfig_name),
        ]),
        tools = [name + "_checker_bin"],
        # Should not run sandboxed.
        tags = [
            "local",
            "manual",
        ],
    )

    #    native.genrule(
    #        name = name + "_tar",
    #    )

    params_file(
        name = name + "_file_list",
        out = name + "_file_list.txt",
        args = ["$(execpath {})".format(f) for f in generated_files],
        data = generated_files,
    )

    #    native.genrule(
    #        name = name + "_tar",
    #        outs = [name + "_tar.tar"],
    #        srcs = generated_files + [name + "_file_list.txt"],
    #        cmd = "pwd && tar -cvf $(location {}) -T $(location {})".format(name + "_tar.tar", name + "_file_list.txt"),
    #    )
    pkg_tar(
        name = name + "_tar",
        srcs = generated_files,
        # Set strip_prefix to keep directory hierarchy in the .tar
        # https://github.com/bazelbuild/rules_pkg/issues/82
        strip_prefix = outdir,
    )

    #    npm_package_bin(
    #        name = name + "_npm_bin",
    #        tool = entry_point,
    #        outs = generated_files,
    #        args = [
    #            "$(execpath {})".format(tsconfig),
    #            outdir,
    #        ],
    #        data = srcs + deps,
    #    )
    return [tsconfig_name] + generated_files

#    templated_args = [
#        "",
#    ]
#    nodejs_test(
#        entry_point,
#        name = name + "_test",
#        entry_point = entry_point,
#        data = srcs + tsconfig,
#        # Should not run sandboxed.
#        tags = [
#            "local",
#            "manual",
#        ],
#    )
