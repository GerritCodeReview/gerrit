load("@rules_shell//shell:sh_test.bzl", "sh_test")

# The to_jakarta transform and this generated-sources checker are provided by
# bazlets (@com_googlesource_gerrit_bazlets//tools). Gerrit selects the
# to_jakarta direction (javax -> jakarta) for its EE10 release flavour and
# carries no renames data; this package only wires the structural guard for the
# generated httpd sources.
#
# The guard asserts the generated EE10 httpd srcjar:
#   * contains no javax.servlet residue (the rewrite was complete), and
#   * contains jakarta.servlet wherever the canonical source had javax.servlet
#     (nothing was dropped),
# while preserving Gerrit's own package names (no com.google.gerrit.*.ee10).
sh_test(
    name = "generated_srcs_test",
    srcs = ["@com_googlesource_gerrit_bazlets//tools:generated_srcs_test.sh"],
    args = [
        "--forbid",
        "javax\\.servlet",
        "--forbid",
        "com\\.google\\.gerrit\\..*\\.ee10",
        "--require-if-source",
        "javax\\.servlet",
        "jakarta\\.servlet",
        "--module",
        "java/",
        "$(location //java/com/google/gerrit/httpd:httpd-ee10-srcs)",
        "$(locations //java/com/google/gerrit/httpd:srcs)",
    ],
    data = [
        "//java/com/google/gerrit/httpd:httpd-ee10-srcs",
        "//java/com/google/gerrit/httpd:srcs",
    ],
)
