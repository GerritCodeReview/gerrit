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

load("@npm//:eslint/package_json.bzl", eslint_bin = "bin")

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
            "//plugins:plugins-config-lib",
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
        srcs: list of files to be checked
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
        {name}_bin rule - runs eslint with specified settings. To use this rule
            pass repo-root-relative or absolute paths, for example:
            bazel run //polygerrit-ui/app:lint_bin -- polygerrit-ui/app
            bazel run //polygerrit-ui/app:lint_bin -- \\
                polygerrit-ui/app/elements/change/gr-change-view/gr-change-view.ts

            Note: {name}_bin is intended for read-only linting. Do not use it with '--fix',
            because ESLint runs on Bazel runfiles paths, which are not writable.
    """

    #TODO(Thomas): Use rules_lint
    bin_data = [
        config,
        "//:node_modules/@eslint/eslintrc",
        "//:node_modules/@eslint/js",
        "//:node_modules/eslint",
    ] + plugins + data

    common_templated_args = [
        "--ext",
        ",".join(extensions),
    ]

    eslint_bin.eslint_test(
        name = name + "_test",
        data = bin_data + srcs,
        chdir = native.package_name(),
        # Bazel generates 2 .js files, where names of the files are generated from the name
        # of the rule: {name}_test_require_patch.js and {name}_test_loader.js
        # Ignore these 2 files, for simplicity do not use {name} in the patterns.
        expand_args = True,
        fixed_args = common_templated_args + [
            "-c",
            config,
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

    # Run from the workspace root so repo-root-relative paths remain inside ESLint's
    # effective base path. Running from the package directory breaks linting of paths
    # outside that directory with flat config:
    # https://github.com/eslint/eslint/issues/19118
    #
    # Note that this rule is for read-only linting only. '--fix' is not supported here,
    # because ESLint operates on Bazel runfiles paths, which are not writable.
    eslint_bin.eslint_binary(
        name = name + "_bin",
        data = bin_data + srcs,
        chdir = "",
        # Bazel generates 2 .js files, where names of the files are generated from the name
        # of the rule: {name}_bin_require_patch.js and {name}_bin_loader.js
        # Ignore these 2 files, for simplicity do not use {name} in the patterns.
        expand_args = True,
        fixed_args = common_templated_args + [
            "-c",
            native.package_name() + "/" + config,
            "--ignore-pattern",
            "*_bin_require_patch.js",
            "--ignore-pattern",
            "*_bin_require_patch.cjs",
            "--ignore-pattern",
            "*_bin_loader.js",
        ],
    )
