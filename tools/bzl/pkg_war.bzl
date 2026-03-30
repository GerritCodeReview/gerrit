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

# Identifiers that should not be tracked in third-party WAR allowlists.
THIRD_PARTY_EXCLUDE_ID_PREFIXES = [
    "com_google_",
    "gerrit_",
]

# Gerrit-internal jars whose normalized IDs do not retain the com_google_/gerrit_
# namespace after normalization and should not appear in third-party allowlists.
THIRD_PARTY_EXCLUDE_ID_EXACT = [
    "index",
    "libcache_proto-speed",
    "libentities_proto-speed",
    "libgerrit-prolog-common",
    "libjgit-archive",
    "libjgit-servlet",
    "libquery_parser",
    "libssh-apache",
    "log4j-config",
]

def war_jar_name(f):
    """Return the jar file name as it will appear inside the WAR.

    Args:
      f: The jar file to process.

    Returns:
      The jar file name as it will appear inside the WAR.
    """
    raw = f.basename
    if raw.startswith(PROCESSED_PREFIX):
        raw = raw[len(PROCESSED_PREFIX):]

    sp = f.short_path

    # Rename ONLY caffeine's "guava" artifact (not Google Guava)
    # Matches: .../com/github/ben-manes/caffeine/guava/<ver>/processed_guava-<ver>.jar
    if "/com/github/ben-manes/caffeine/guava/" in sp and raw.startswith("guava-") and raw.endswith(".jar"):
        raw = "caffeine-" + raw  # -> caffeine-guava-<ver>.jar

    # Keep existing Gerrit naming rules
    if sp.startswith("gerrit-"):
        raw = sp.split("/")[0] + "-" + raw
    elif sp.startswith("java/"):
        raw = sp[5:].replace("/", "_")

    return raw

def normalize_jar_id(jar_name):
    """Version-agnostic jar identity used for allowlists/inventories.

    Args:
      jar_name: The original jar name.

    Returns:
      The version-agnostic jar identity.
    """
    n = jar_name
    if n.endswith(".jar"):
        n = n[:-4]
    i = n.rfind("-")

    # Strip trailing "-<version-ish>" where the suffix begins with a digit.
    if i > 0 and n[i + 1:i + 2].isdigit():
        n = n[:i]
    return n

def should_skip_packaged_jar(jar_name):
    """Returns True if the packaged jar should be skipped.

    jar_name must be the post-processed name (war_jar_name output).

    Args:
      jar_name: The post-processed jar name.

    Returns:
      True if the packaged jar should be skipped, False otherwise.
    """
    for pfx in EXCLUDE_WAR_JAR_PREFIXES:
        if jar_name.startswith(pfx):
            return True

    # Bazel 8: skip protobuf runtime shards (duplicate protobuf-java)
    if jar_name in ("libcore.jar", "liblite_runtime_only.jar"):
        return True
    return False

def is_third_party_jar_id(jar_id):
    """Return True if jar_id should be tracked in third-party allowlists.

    Args:
      jar_id: The version-agnostic jar identity.

    Returns:
      True if jar_id should be tracked in third-party allowlists, False otherwise.
    """
    if jar_id in THIRD_PARTY_EXCLUDE_ID_EXACT:
        return False
    for pfx in THIRD_PARTY_EXCLUDE_ID_PREFIXES:
        if jar_id.startswith(pfx):
            return False
    return True

def _add_context(in_file, output):
    return [
        "unzip -qd %s %s" % (output, in_file.path),
    ]

def _add_file(in_file, output):
    raw = war_jar_name(in_file)
    output_path = output + raw
    return [
        "test -L %s || ln -s $(pwd)/%s %s" % (output_path, in_file.path, output_path),
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

def _ci_sorted(xs):
    return sorted(xs, key = lambda s: (s.lower(), s))

def _war_impl(ctx):
    war = ctx.outputs.war
    build_output = war.path + ".build_output"
    inputs = []

    # Metadata we expose for checks/tools.
    jar_entries = []
    jar_ids = []

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
        packaged = war_jar_name(dep)
        if should_skip_packaged_jar(packaged):
            continue

        cmd += _add_file(dep, build_output + "/WEB-INF/lib/")
        inputs.append(dep)

        jar_entries.append("WEB-INF/lib/" + packaged)

        jid = normalize_jar_id(packaged)
        if is_third_party_jar_id(jid):
            jar_ids.append(jid)

    # Add pgm libs
    transitive_pgmlibs = []
    for j in ctx.attr.pgmlibs:
        transitive_pgmlibs.append(j[JavaInfo].transitive_runtime_jars)

    for dep in depset(transitive = transitive_pgmlibs).to_list():
        packaged = war_jar_name(dep)
        if should_skip_packaged_jar(packaged):
            continue

        if dep not in inputs:
            cmd += _add_file(dep, build_output + "/WEB-INF/pgm-lib/")
            inputs.append(dep)

            jar_entries.append("WEB-INF/pgm-lib/" + packaged)

            jid = normalize_jar_id(packaged)
            if is_third_party_jar_id(jid):
                jar_ids.append(jid)

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

    # Write deterministic manifests for checks.
    #
    # NOTE: The manifests are produced as independent actions.
    # Bazel will only execute the actions needed for the requested output,
    # so building *.war.entries.txt does not materialize the WAR.
    ctx.actions.write(
        output = ctx.outputs.jars,
        content = "\n".join(_ci_sorted(depset(jar_ids).to_list())) + "\n",
    )
    ctx.actions.write(
        output = ctx.outputs.entries,
        content = "\n".join(_ci_sorted(jar_entries)) + "\n",
    )

    # Add zip war
    cmd.append(_make_war(build_output, war))

    ctx.actions.run_shell(
        inputs = inputs,
        outputs = [war],
        mnemonic = "WAR",
        command = "\n".join(cmd),
        use_default_shell_env = True,
    )

    return [
        DefaultInfo(files = depset([war, ctx.outputs.jars, ctx.outputs.entries])),
    ]

# context: go to the root directory
# libs: go to the WEB-INF/lib directory
# pgmlibs: go to the WEB-INF/pgm-lib directory
_pkg_war = rule(
    attrs = {
        "context": attr.label_list(allow_files = True),
        "libs": attr.label_list(allow_files = jar_filetype),
        "pgmlibs": attr.label_list(allow_files = False),
    },
    outputs = {
        "war": "%{name}.war",
        "jars": "%{name}.war.jars.txt",
        "entries": "%{name}.war.entries.txt",
    },
    implementation = _war_impl,
)

def pkg_war(name, ui = "polygerrit", context = [], doc = False, **kwargs):
    """Rule for packaging the Gerrit WAR.

    Args:
      name: The name of the target.
      ui: The UI type, e.g. "polygerrit".
      context: The list of context dependencies.
      doc: Whether to include documentation.
      **kwargs: Additional keyword arguments.
    """
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
