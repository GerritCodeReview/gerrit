# Copyright 2022 Aspect Build Systems, Inc.
# Copyright 2025 The Android Open Source Project

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at

#     http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Modified and minified version of https://github.com/aspect-build/rules_terser
# to deal with node_modules locations in the Gerrit project. Further, style was
# modified to match Gerrit's coding style.

load("@aspect_bazel_lib//lib:copy_file.bzl", "copy_file")
load("@aspect_bazel_lib//lib:copy_to_bin.bzl", "COPY_FILE_TO_BIN_TOOLCHAINS", "copy_files_to_bin_actions")
load("@aspect_rules_js//js:defs.bzl", "js_binary")
load("@aspect_rules_js//js:libs.bzl", "js_lib_helpers")
load("@aspect_rules_js//js:providers.bzl", "js_info")

_ATTRS = {
    "srcs": attr.label_list(
        allow_files = [".js", ".map", ".mjs"],
        mandatory = True,
    ),
    "terser": attr.label(
        mandatory = True,
        executable = True,
        cfg = "exec",
    ),
}

def _filter_js(files):
    return [f for f in files if f.is_directory or f.extension == "js" or f.extension == "mjs"]

def _impl(ctx):
    args = ctx.actions.args()

    inputs = copy_files_to_bin_actions(ctx, ctx.files.srcs)

    input_sources = _filter_js(inputs)
    input_dir_sources = [s for s in input_sources if s.is_directory]

    output_sources = []

    if len(input_dir_sources) > 0:
        if len(input_sources) > 1:
            fail("When directories are passed to terser, there should be only one input")
        output_sources.append(ctx.actions.declare_directory(ctx.label.name))
    else:
        output_sources.append(ctx.actions.declare_file("%s.js" % ctx.label.name))

    args.add_all([s.short_path for s in input_sources])
    args.add_all(["--output", output_sources[0].short_path])

    options = ctx.actions.declare_file("_%s.minify_options.json" % ctx.label.name)
    inputs.append(options)
    ctx.actions.write(
        output = options,
        content = """
{
    "compress": {
        "keep_fnames": true,
        "passes": 3,
        "pure_getters": true,
        "reduce_funcs": true,
        "reduce_vars": true,
        "sequences": true
    },
    "mangle": true
}
"""
    )
    args.add_all(["--config-file", options.short_path])

    ctx.actions.run(
        inputs = inputs,
        outputs = output_sources,
        executable = ctx.executable.terser,
        arguments = [args],
        env = {
            "COMPILATION_MODE": ctx.var["COMPILATION_MODE"],
            "BAZEL_BINDIR": ctx.bin_dir.path,
        },
        mnemonic = "TerserMinify",
        progress_message = "Minifying JavaScript %{output}",
    )

    output_sources_depset = depset(output_sources)

    transitive_sources = js_lib_helpers.gather_transitive_sources(
        sources = output_sources,
        targets = ctx.attr.srcs,
    )

    transitive_types = js_lib_helpers.gather_transitive_types(
        types = [],
        targets = ctx.attr.srcs,
    )

    npm_sources = js_lib_helpers.gather_npm_sources(
        srcs = ctx.attr.srcs,
        deps = [],
    )

    npm_package_store_infos = js_lib_helpers.gather_npm_package_store_infos(
        targets = ctx.attr.srcs,
    )

    runfiles = js_lib_helpers.gather_runfiles(
        ctx = ctx,
        sources = transitive_sources,
        deps = ctx.attr.srcs,
    )

    return [
        js_info(
            target = ctx.label,
            sources = output_sources_depset,
            types = depset(),  # terser does not emit types directly
            transitive_sources = transitive_sources,
            transitive_types = transitive_types,
            npm_sources = npm_sources,
            npm_package_store_infos = npm_package_store_infos,
        ),
        DefaultInfo(
            files = output_sources_depset,
            runfiles = runfiles,
        ),
    ]

terser_lib = struct(
    attrs = _ATTRS,
    implementation = _impl,
    toolchains = COPY_FILE_TO_BIN_TOOLCHAINS,
)

_terser = rule(
    implementation = terser_lib.implementation,
    attrs = terser_lib.attrs,
    toolchains = terser_lib.toolchains,
)

def terser(
        name,
        node_modules,
        srcs):
    """Run the terser minifier.

    Typical example:

    ```starlark
    load("//tools/terser:defs.bzl", "terser")

    terser(
        name = "out.min",
        srcs = "input.js",
    )
    ```

    Note that the `name` attribute determines what the resulting files will be called.

    If the input is a directory, then the output will also be a directory, named after the `name` attribute.

    Note that this rule is **NOT** recursive. It assumes a flat file structure. Passing in a folder with nested folder
    will result in an empty output directory.

    Args:
        name: A unique name for this target.

        node_modules: Label pointing to the linked node_modules target where the `terser` is linked, e.g. `//:node_modules`.

            `terser` must be linked into the node_modules supplied.

        srcs: File(s) to minify.

            Can be `.js` files, a rule producing `.js` files as its default output, or a rule producing a directory of `.js` files.

            If multiple files are passed, terser will bundle them together.
    """
    entry_point = "_{}_terser_runner.cjs".format(name)
    copy_file(
        name = "_{}_copy_terser_runner".format(name),
        src = "//tools/terser:runner.cjs",
        out = entry_point,
    )

    terser = "_{}_terser_binary".format(name)
    js_binary(
        name = terser,
        data = ["{}/terser".format(node_modules)],
        entry_point = entry_point,
    )

    _terser(
        name = name,
        srcs = srcs,
        terser = terser
    )
