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

load("@aspect_rules_js//js:defs.bzl", "js_binary", "js_test")

def plugin_eslint():
    """ Convenience wrapper macro of eslint() for Gerrit js plugins

    Args:
        name: name of the rule
    """
    eslint(
        name = "lint",
        srcs = native.glob(["**/*.ts"]),
        config = "eslint.config.js",
        data = [
            "tsconfig.json",
            "//plugins:eslint.config.js",
            "//plugins:.prettierrc.js",
            "//plugins:tsconfig-plugins-base.json",
            "//:node_modules/typescript",
        ],
        extensions = [".ts"],
        plugins = [
            "//:node_modules/eslint-config-google",
            "//:node_modules/eslint-plugin-html",
            "//:node_modules/eslint-plugin-import",
            "//:node_modules/eslint-plugin-jsdoc",
            "//:node_modules/eslint-plugin-lit",
            "//:node_modules/eslint-plugin-n",
            "//:node_modules/eslint-plugin-prettier",
            "//:node_modules/eslint-plugin-regex",
            "//:node_modules/gts",
        ],
    )

def eslint(name, plugins, srcs, config, size = "large", extensions = [".js"], data = []):
    """ Macro to define eslint rules for files.

    Args:
        name: name of the rule
        plugins: list of npm dependencies with plugins, for example "//:node_modules/eslint-config-google"
        srcs: list of files to be checked (ignored in {name}_bin rule)
        config: eslint config file
        size: eslint test size, supported values are: small, medium, large and enormous,
            with implied timeout labels: short, moderate, long, and eternal
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
    entry_point = "//:node_modules/eslint/bin/eslint.js"

    #TODO(Thomas): Use rules_lint
    bin_data = [
        "//:node_modules/eslint",
        config,
        "//tools/js:eslint-chdir.js",
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
    ]
    js_test(
        name = name + "_test",
        entry_point = entry_point,
        data = bin_data + srcs,
        # Bazel generates 2 .js files, where names of the files are generated from the name
        # of the rule: {name}_test_require_patch.js and {name}_test_loader.js
        # Ignore these 2 files, for simplicity do not use {name} in the patterns.
        expand_args = True,
        fixed_args = common_templated_args + [
            "--ignore-pattern",
            "*_test_require_patch.js",
            "--ignore-pattern",
            "*_test_require_patch.cjs",
            "--ignore-pattern",
            "*_test_loader.js",
            "./",  # Relative to the config file location
        ],
        # Should not run sandboxed.
        tags = [
            "local",
            "manual",
        ],
        size = size,
    )

    js_binary(
        name = name + "_bin",
        entry_point = "//:node_modules/eslint/bin/eslint.js",
        data = bin_data,
        # Bazel generates 2 .js files, where names of the files are generated from the name
        # of the rule: {name}_bin_require_patch.js and {name}_bin_loader.js
        # Ignore these 2 files, for simplicity do not use {name} in the patterns.
        expand_args = True,
        fixed_args = common_templated_args + [
            "--ignore-pattern",
            "*_bin_require_patch.js",
            "--ignore-pattern",
            "*_bin_require_patch.cjs",
            "--ignore-pattern",
            "*_bin_loader.js",
        ],
    )
