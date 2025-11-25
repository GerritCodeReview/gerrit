load("@rules_shell//shell:sh_test.bzl", "sh_test")

exports_files([
    "license-map.py",
    "test_empty.sh",
    "test_license.sh",
])

sh_test(
    name = "always_pass_test",
    srcs = ["always_pass_test.sh"],
    tags = ["no_rbe"],
)
