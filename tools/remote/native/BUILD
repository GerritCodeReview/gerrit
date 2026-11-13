load("@rules_shell//shell:sh_binary.bzl", "sh_binary")
load(":native_tools.bzl", "native_tools")

native_tools(
    name = "tools",
    debs = [
        "@rbe_native_tools//git/amd64",
        "@rbe_native_tools//openssh-client/amd64",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    visibility = ["//visibility:public"],
)

# Used as --run_under in the rbe config (see tools/meroton-bazelrc): puts git,
# ssh and ssh-keygen on the PATH of every test.
sh_binary(
    name = "with_native_tools",
    srcs = ["with_native_tools.sh"],
    data = [":tools"],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    visibility = ["//visibility:public"],
)
