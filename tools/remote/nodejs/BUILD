load("@rules_nodejs//nodejs:toolchain.bzl", "nodejs_toolchain")
load("@rules_shell//shell:sh_binary.bzl", "sh_binary")

sh_binary(
    name = "make_nodejs_bundle",
    srcs = ["make_nodejs_bundle.sh"],
)

genrule(
    name = "nodejs_with_libatomic",
    srcs = [
        "nodejs_stub.sh",
        "@libatomic_ubuntu_24_04//:usr/lib/x86_64-linux-gnu/libatomic.so.1.2.0",
        "@nodejs_linux_amd64//:node_bin",
    ],
    outs = ["nodejs_with_libatomic"],
    # Arguments: OUT STUB NODE LIBATOMIC
    cmd = """$(execpath :make_nodejs_bundle) $@ \
          "$(execpath nodejs_stub.sh)" \
          "$(execpath @nodejs_linux_amd64//:node_bin)" \
          "$(execpath @libatomic_ubuntu_24_04//:usr/lib/x86_64-linux-gnu/libatomic.so.1.2.0)"
    """,
    executable = True,
    tools = [":make_nodejs_bundle"],
    exec_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
)

nodejs_toolchain(
    name = "nodejs_toolchain_impl",
    node = ":nodejs_with_libatomic",
)

# Only registered through --extra_toolchains in the rbe config, so local
# builds keep using the regular Node.js toolchain.
toolchain(
    name = "nodejs_toolchain",
    exec_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    toolchain = ":nodejs_toolchain_impl",
    toolchain_type = "@rules_nodejs//nodejs:toolchain_type",
)

toolchain(
    name = "nodejs_toolchain_runtime",
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    toolchain = ":nodejs_toolchain_impl",
    toolchain_type = "@rules_nodejs//nodejs:runtime_toolchain_type",
)
