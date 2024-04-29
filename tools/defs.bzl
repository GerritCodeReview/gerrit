load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

def gerrit_init():
    """
    Initialize the WORKSPACE for gerrit targets
    """
    protobuf_deps()

    native.register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

    native.register_toolchains("//tools:error_prone_warnings_toolchain_java21_definition")
