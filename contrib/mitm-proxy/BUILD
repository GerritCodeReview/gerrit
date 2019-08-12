load("@io_bazel_rules_go//go:def.bzl", "go_binary")

package(default_visibility = ["//visibility:public"])

go_binary(
    name = "balancer",
    srcs = ["mitm-balancer.go"],
)

sh_binary(
    name = "mitmproxy",
    srcs = ["mitmproxy.sh"],
    args = [
        "-f $(location //polygerrit-ui:fonts.zip)",
        "-t $(location //polygerrit-ui/app:test_components.zip)",
        "-b $(location :balancer)",
        "-s $(location scripts/gerrit-addon.py)",
    ],
    data = glob(["scripts/*.py"]) + [
        ":balancer",
        "//polygerrit-ui:fonts.zip",
        "//polygerrit-ui/app:test_components.zip",
    ],
)
