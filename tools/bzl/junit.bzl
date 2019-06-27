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

# Skylark rule to generate a Junit4 TestSuite
# Assumes srcs are all .java Test files
# Assumes junit4 is already added to deps by the user.

# See https://github.com/bazelbuild/bazel/issues/1017 for background.

_OUTPUT = """import org.junit.runners.Suite;
import org.junit.runner.RunWith;

@RunWith(Suite.class)
@Suite.SuiteClasses({%s})
public class %s {}
"""

_PREFIXES = ("org", "com", "edu")

def _SafeIndex(j, val):
    for i, v in enumerate(j):
        if val == v:
            return i
    return -1

def _AsClassName(fname):
    fname = [x.path for x in fname.files.to_list()][0]
    toks = fname[:-5].split("/")
    findex = -1
    for s in _PREFIXES:
        findex = _SafeIndex(toks, s)
        if findex != -1:
            break
    if findex == -1:
        fail("%s does not contain any of %s" % (fname, _PREFIXES))
    return ".".join(toks[findex:]) + ".class"

def _impl(ctx):
    classes = ",".join(
        [_AsClassName(x) for x in ctx.attr.srcs],
    )
    ctx.actions.write(output = ctx.outputs.out, content = _OUTPUT % (
        classes,
        ctx.attr.outname,
    ))

_GenSuite = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "outname": attr.string(),
    },
    outputs = {"out": "%{name}.java"},
    implementation = _impl,
)

POST_JDK8_OPTS = [
    # Enforce JDK 8 compatibility on Java 9, see
    # https://docs.oracle.com/javase/9/intl/internationalization-enhancements-jdk-9.htm#JSINT-GUID-AF5AECA7-07C1-4E7D-BC10-BC7E73DC6C7F
    "-Djava.locale.providers=COMPAT,CLDR,SPI",
    "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED",
]

def junit_tests(name, srcs, **kwargs):
    s_name = name.replace("-", "_") + "TestSuite"
    _GenSuite(
        name = s_name,
        srcs = srcs,
        outname = s_name,
    )
    jvm_flags = kwargs.get("jvm_flags", [])
    jvm_flags = jvm_flags + select({
        "//:java9": POST_JDK8_OPTS,
        "//:java_next": POST_JDK8_OPTS,
        "//conditions:default": [],
    })
    native.java_test(
        name = name,
        test_class = s_name,
        srcs = srcs + [":" + s_name],
        **dict(kwargs, jvm_flags = jvm_flags)
    )
