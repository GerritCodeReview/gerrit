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

# Javadoc rule.

load("@rules_java//java:defs.bzl", "JavaInfo")
load("//tools/bzl:jar.bzl", "JAR_FLAGS")

_JAVA_TOOLCHAIN_TYPE = "@bazel_tools//tools/jdk:toolchain_type"

def _impl(ctx):
    zip_output = ctx.outputs.zip
    java_runtime = ctx.toolchains[_JAVA_TOOLCHAIN_TYPE].java.java_runtime

    transitive_jars = depset(transitive = [j[JavaInfo].transitive_compile_time_jars for j in ctx.attr.libs])

    # TODO(davido): Remove list to depset conversion on source_jars, when this issue is fixed:
    # https://github.com/bazelbuild/bazel/issues/4221
    source_jars = depset(transitive = [depset(j[JavaInfo].source_jars) for j in ctx.attr.libs])

    transitive_jar_paths = [j.path for j in transitive_jars.to_list()]
    dir = ctx.outputs.zip.path + ".dir"
    source = ctx.outputs.zip.path + ".source"
    external_docs = ["https://docs.oracle.com/en/java/javase/11/docs/api"] + ctx.attr.external_docs
    cmd = [
        "TZ=UTC",
        "export TZ",
        "root=$(pwd)",
        "jar=$(cd %s && pwd)/bin/jar" % java_runtime.java_home,
        "rm -rf %s" % source,
        "mkdir %s" % source,
        " && ".join(['(cd %s && "$jar" xf "$root/%s")' % (source, j.path) for j in source_jars.to_list()]),
        "rm -rf %s" % dir,
        "mkdir %s" % dir,
        " ".join([
            "%s/bin/javadoc" % java_runtime.java_home,
            " ".join(["-J%s" % opt for opt in ctx.fragments.java.default_jvm_opts]),
            "-Xdoclint:-missing",
            "-protected",
            "-encoding UTF-8",
            "-charset UTF-8",
            "-notimestamp",
            "-quiet",
            "-windowtitle '%s'" % ctx.attr.title,
            " ".join(["-link %s" % url for url in external_docs]),
            "-sourcepath %s" % source,
            "-subpackages ",
            ":".join(ctx.attr.pkgs),
            " -classpath ",
            ":".join(transitive_jar_paths),
            "-d %s" % dir,
        ]),
        "find %s -exec touch -t 198001010000 '{}' ';'" % dir,
        '(cd %s && "$jar" %s --file ../%s *)' % (dir, JAR_FLAGS, ctx.outputs.zip.basename),
    ]
    ctx.actions.run_shell(
        inputs = transitive_jars.to_list() + source_jars.to_list() + java_runtime.files.to_list(),
        outputs = [zip_output],
        command = " && ".join(cmd),
    )

java_doc = rule(
    attrs = {
        "external_docs": attr.string_list(),
        "libs": attr.label_list(allow_files = False),
        "pkgs": attr.string_list(),
        "title": attr.string(),
    },
    # Emit a .jar directly (a javadoc jar is a zip). This lets the artifact be
    # consumed as a Maven `javadoc` classifier without a rename step, and matches
    # the conventional `-javadoc.jar` name.
    outputs = {"zip": "%{name}.jar"},
    implementation = _impl,
    fragments = ["java"],
    toolchains = [_JAVA_TOOLCHAIN_TYPE],
)
