load("@rules_java//java:defs.bzl", "java_import", "java_library")
load("//lib/codemirror:cm.bzl", "pkg_cm")

# This library is only used to insert a license statement into
# js_licenses.txt.
java_library(
    name = "diff-match-patch",
    data = ["//lib:LICENSE-Apache2.0"],
    runtime_deps = ["@diff-match-patch//jar"],
)

pkg_cm()

LICENSE = "//lib:LICENSE-codemirror-original"

LICENSE_MINIFIED = "//lib:LICENSE-codemirror-minified"

[java_import(
    name = "codemirror" + suffix,
    data = [license],
    jars = [":jar%s" % suffix],
    visibility = ["//visibility:public"],
) for suffix, license in [
    ("", LICENSE),
    ("_r", LICENSE_MINIFIED),
]]
