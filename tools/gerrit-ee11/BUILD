load("@rules_shell//shell:sh_test.bzl", "sh_test")

# The servlet-flavour transform and this generated-sources checker are
# provided by bazlets (@com_googlesource_gerrit_bazlets//tools). Gerrit's
# servlet boundary sources are jakarta-canonical: the default EE8 flavour
# is generated through the to_javax direction (jakarta -> javax) and
# carries no renames data; this package only wires the structural guard
# for the generated httpd sources.
#
# The guard asserts the generated EE8 httpd srcjar:
#   * contains no jakarta.servlet residue (the rewrite was complete), and
#   * contains javax.servlet wherever the canonical source had
#     jakarta.servlet (nothing was dropped),
# while preserving Gerrit's own package names (no com.google.gerrit.*.ee8).
sh_test(
    name = "generated_srcs_test",
    srcs = ["@com_googlesource_gerrit_bazlets//tools:generated_srcs_test.sh"],
    args = [
        "--forbid",
        "jakarta\\.servlet",
        "--forbid",
        "com\\.google\\.gerrit\\..*\\.ee8",
        "--require-if-source",
        "jakarta\\.servlet",
        "javax\\.servlet",
        "--module",
        "java/",
        "$(location //java/com/google/gerrit/httpd:httpd-ee8-srcs)",
        "$(locations //java/com/google/gerrit/httpd:srcs)",
    ],
    data = [
        "//java/com/google/gerrit/httpd:httpd-ee8-srcs",
        "//java/com/google/gerrit/httpd:srcs",
    ],
)
