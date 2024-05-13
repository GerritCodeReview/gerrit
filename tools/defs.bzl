load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")
load("@bazel_tools//tools/jdk:remote_java_repository.bzl", "remote_java_repository")

def gerrit_init():
    """
    Initialize the WORKSPACE for gerrit targets
    """
    protobuf_deps()

    remote_java_repository(
        name = "openjdk_canary_linux",
        prefix = "openjdk_canary",
#        exec_compatible_with = [
#            "@platforms//cpu:x86_64",
#            "@platforms//os:linux",
#        ],
        sha256 = "993d91062c631d10508475f7b112724fa8136704ec1412d5cc1f93ddda1eddb0",
        strip_prefix = "zulu22.30.13-ca-jdk22.0.1-linux_x64",
        urls = ["https://cdn.azul.com/zulu/bin/zulu22.30.13-ca-jdk22.0.1-linux_x64.tar.gz"],
        version = "22",
    )

    native.register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

    native.register_toolchains("//tools:error_prone_warnings_toolchain_java21_definition")

    native.register_toolchains("//tools:error_prone_warnings_toolchain_java22_definition")
