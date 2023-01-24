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

"""This file contains macro to run eslint and define a eslint test rule."""

load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary", "nodejs_test")

def eslint(name, plugins, srcs, config, ignore, extensions = [".js"], data = []):
    """ Macro to define eslint rules for files.

    Args:
        name: name of the rule
        plugins: list of npm dependencies with plugins, for example "@npm//eslint-config-google"
        srcs: list of files to be checked (ignored in {name}_bin rule)
        config: eslint config file
        ignore: eslint ignore file
        extensions: list of file extensions to be checked. This is an additional filter for
            srcs list. Each extension must start with '.' character.
            Default: [".js"].
        data: list of additional dependencies. For example if a config file extends an another
            file, this other file must be added to data.

    Generate: 2 rules:
        {name}_test rule - runs eslint tests. You can run this rule with
            'bazel test {name}_test' command. The rule tests all files from srcs with specified
            extensions inside the package where eslint macro is called.
        {name}_bin rule - runs eslint with specified settings; ignores srcs. To use this rule
            you must pass a folder to check, for example:
            bazel run {name}_test -- --fix $(pwd)/polygerrit-ui/app
    """
    entry_point = "@npm//:node_modules/eslint/bin/eslint.js"

    # There are custom eslint rules in eslint-rules directory. Eslint loads
    # custom rules from a directory specified with the --rulesdir argument.
    # When bazel runs eslint, it places the eslint-rules directory into
    # some location in the filesystem, and the location is not known in advance.
    # It is not possible to get the directory location in bazel directly.
    # Instead, we can use dirname to get a directory for a file in the
    # eslint-rules directory.
    # README.md is the most "stable" file in the eslint-rules directory
    # (i.e. it is unlikely will be removed), and we are using it to calculate
    # exact directory path in bazel.
    eslint_rules_toplevel_file = "//tools/js/eslint-rules:README.md"
    bin_data = [
        "@npm//eslint:eslint",
        config,
        ignore,
        "//tools/js/eslint-rules:eslint-rules-srcs",
        "//tools/js:eslint-chdir.js",
        eslint_rules_toplevel_file,
    ] + plugins + data
    common_templated_args = [
        "--node_options=--require=$$(rlocation $(rootpath //tools/js:eslint-chdir.js))",
        "--ext",
        ",".join(extensions),
        "-c",
        # Use rlocation/rootpath instead of location.
        # See note and example here:
        # https://bazelbuild.github.io/rules_nodejs/Built-ins.html#nodejs_binary
        "$$(rlocation $(rootpath {}))".format(config),
        "--ignore-path",
        "$$(rlocation $(rootpath {}))".format(ignore),
        # Load custom rules from eslint-rules directory
        "--rulesdir",
        "$$(dirname $$(rlocation $(rootpath {})))".format(eslint_rules_toplevel_file),
    ]
    nodejs_test(
        name = name + "_test",
        entry_point = entry_point,
        data = bin_data + srcs,
        # Bazel generates 2 .js files, where names of the files are generated from the name
        # of the rule: {name}_test_require_patch.js and {name}_test_loader.js
        # Ignore these 2 files, for simplicity do not use {name} in the patterns.
        templated_args = common_templated_args + [
            "--ignore-pattern",
            "*_test_require_patch.js",
            "--ignore-pattern",
            "*_test_loader.js",
            "./",  # Relative to the config file location
        ],
        # Should not run sandboxed.
        tags = [
            "local",
            "manual",
        ],
    )

    nodejs_binary(
        name = name + "_bin",
        entry_point = "@npm//:node_modules/eslint/bin/eslint.js",
        data = bin_data,
        # Bazel generates 2 .js files, where names of the files are generated from the name
        # of the rule: {name}_bin_require_patch.js and {name}_bin_loader.js
        # Ignore these 2 files, for simplicity do not use {name} in the patterns.
        templated_args = common_templated_args + [
            "--ignore-pattern",
            "*_bin_require_patch.js",
            "--ignore-pattern",
            "*_bin_loader.js",
        ],
    )
