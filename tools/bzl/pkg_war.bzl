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

load("@rules_java//java:defs.bzl", "JavaInfo")

jar_filetype = [".jar"]

LIBS = [
    "//java/com/google/gerrit/common:version",
    "//java/com/google/gerrit/httpd/init",
    "//lib/bouncycastle:bcpkix",
    "//lib/bouncycastle:bcprov",
    "//lib/bouncycastle:bcpg",
    "//lib/log:impl-log4j",
    "//prolog:gerrit-prolog-common",
    "//resources:log4j-config",
]

PGMLIBS = [
    "//java/com/google/gerrit/pgm",
]

# Special prefix added by rules_jvm_external.jvm_import() to stamped jars
# https://github.com/bazel-contrib/rules_jvm_external/blob/6.9/private/rules/jvm_import.bzl#L32
PROCESSED_PREFIX = "processed_"

# Jars that must not be packaged into release.war.
#
# Keep this list prefix-based and version-agnostic so it remains stable
# across dependency upgrades.
EXCLUDE_WAR_JAR_PREFIXES = [
    # Codegen / annotation processor support libs (compile-time only).
    "autotransient-",
    "auto-",
    "javapoet-",
    "checker-qual-",
    "checker-compat-qual-",
    "error_prone_annotations-",
    "jspecify-",
    "jsinterop-annotations-",

    # Placeholder jar used to avoid conflicts with Guava.
    "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava",
]

def _add_context(in_file, output):
    return [
        "unzip -qd %s %s" % (output, in_file.path),
    ]

def _should_skip_runtime_jar(dep):
    # Do not package any jars coming from jgit_deps.
    if "jgit_deps" in dep.path:
        return True

    raw = dep.basename
    if raw.startswith(PROCESSED_PREFIX):
        raw = raw[len(PROCESSED_PREFIX):]

    for pfx in EXCLUDE_WAR_JAR_PREFIXES:
        if raw.startswith(pfx):
            return True

    return False

def _add_file(in_file, output):
    input_path = in_file.path
    short_path = in_file.short_path
    n = in_file.basename

    # Strip rules_jvm_external processed_ prefix for naming decisions
    raw = n
    if raw.startswith(PROCESSED_PREFIX):
        raw = raw[len(PROCESSED_PREFIX):]

    # Rename ONLY caffeine's "guava" artifact (not Google Guava)
    # Matches: .../com/github/ben-manes/caffeine/guava/<ver>/processed_guava-<ver>.jar
    if "/com/github/ben-manes/caffeine/guava/" in short_path and raw.startswith("guava-") and raw.endswith(".jar"):
        raw = "caffeine-" + raw  # -> caffeine-guava-<ver>.jar

    # Keep existing Gerrit naming rules
    if short_path.startswith("gerrit-"):
        raw = short_path.split("/")[0] + "-" + raw
    elif short_path.startswith("java/"):
        raw = short_path[5:].replace("/", "_")

    output_path = output + raw
    return [
        "test -L %s || ln -s $(pwd)/%s %s" % (output_path, input_path, output_path),
    ]

def _make_war(input_dir, output):
    return "(%s)" % " && ".join([
        "root=$(pwd)",
        "TZ=UTC",
        "export TZ",
        "cd %s" % input_dir,
        "find . -exec touch -t 198001010000 '{}' ';' 2> /dev/null",
        "zip -X -9qr ${root}/%s ." % (output.path),
    ])

def _war_impl(ctx):
    war = ctx.outputs.war
    build_output = war.path + ".build_output"
    inputs = []

    # Create war layout
    cmd = [
        "set -e;rm -rf " + build_output,
        "mkdir -p " + build_output,
        "mkdir -p %s/WEB-INF/lib" % build_output,
        "mkdir -p %s/WEB-INF/pgm-lib" % build_output,
    ]

    # Add runtime libs
    transitive_libs = []
    for j in ctx.attr.libs:
        if JavaInfo in j:
            transitive_libs.append(j[JavaInfo].transitive_runtime_jars)
        elif hasattr(j, "files"):
            transitive_libs.append(j.files)

    for dep in depset(transitive = transitive_libs).to_list():
        if _should_skip_runtime_jar(dep):
            continue
        cmd += _add_file(dep, build_output + "/WEB-INF/lib/")
        inputs.append(dep)

    # Add pgm libs
    transitive_pgmlibs = []
    for j in ctx.attr.pgmlibs:
        transitive_pgmlibs.append(j[JavaInfo].transitive_runtime_jars)

    for dep in depset(transitive = transitive_pgmlibs).to_list():
        if _should_skip_runtime_jar(dep):
            continue
        if dep not in inputs:
            cmd += _add_file(dep, build_output + "/WEB-INF/pgm-lib/")
            inputs.append(dep)

    # Add context
    transitive_context_libs = []
    if ctx.attr.context:
        for jar in ctx.attr.context:
            if JavaInfo in jar:
                transitive_context_libs.append(jar[JavaInfo].transitive_runtime_jars)
            elif hasattr(jar, "files"):
                transitive_context_libs.append(jar.files)

    for dep in depset(transitive = transitive_context_libs).to_list():
        cmd += _add_context(dep, build_output)
        inputs.append(dep)

    # Add zip war
    cmd.append(_make_war(build_output, war))

    ctx.actions.run_shell(
        inputs = inputs,
        outputs = [war],
        mnemonic = "WAR",
        command = "\n".join(cmd),
        use_default_shell_env = True,
    )

# context: go to the root directory
# libs: go to the WEB-INF/lib directory
# pgmlibs: go to the WEB-INF/pgm-lib directory
_pkg_war = rule(
    attrs = {
        "context": attr.label_list(allow_files = True),
        "libs": attr.label_list(allow_files = jar_filetype),
        "pgmlibs": attr.label_list(allow_files = False),
    },
    outputs = {"war": "%{name}.war"},
    implementation = _war_impl,
)

def pkg_war(name, ui = "polygerrit", context = [], doc = False, **kwargs):
    doc_ctx = []
    doc_lib = []
    ui_deps = []
    if ui == "polygerrit":
        ui_deps.append("//polygerrit-ui/app:polygerrit_ui")
    if doc:
        doc_ctx.append("//Documentation:html")
        doc_lib.append("//Documentation:index")

    _pkg_war(
        name = name,
        libs = LIBS + doc_lib,
        pgmlibs = PGMLIBS,
        context = doc_ctx + context + ui_deps + [
            "//java:gerrit-main-class_deploy.jar",
            "//webapp:assets",
        ],
        **kwargs
    )
