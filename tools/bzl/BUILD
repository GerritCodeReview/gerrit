load("@rules_shell//shell:sh_test.bzl", "sh_test")

exports_files([
    "diff_allowlist.sh",
    "license-map.py",
    "test_empty.sh",
    "test_license.sh",
    "war_checks.bzl",
])

sh_test(
    name = "always_pass_test",
    srcs = ["always_pass_test.sh"],
    tags = ["no_rbe"],
)
