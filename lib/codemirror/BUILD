load("//lib/codemirror:cm.bzl", "pkg_cm")

# This library is only used to insert a license statement into
# js_licenses.txt.
java_library(
    name = "diff-match-patch",
    runtime_deps = [ "@diff_match_patch//jar" ],
    data = [ "//lib:LICENSE-Apache2.0" ],
)

pkg_cm()
