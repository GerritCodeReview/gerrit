load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "aopalliance",
    data = ["//lib:LICENSE-PublicDomain"],
    exports = ["@external_deps//:aopalliance_aopalliance"],
)

java_library(
    name = "jakarta-inject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:jakarta_inject_jakarta_inject_api"],
)

# Guice 7 uses jakarta.inject (not javax.inject), so guice exports
# jakarta-inject.
java_library(
    name = "guice",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guice-library",
        ":jakarta-inject",
    ],
)

java_library(
    name = "guice-library",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        # The classes classifier ships Guice 7 unshaded; the external ASM
        # replaces the shaded copy that cannot read Java 25 class files.
        "//lib/ow2:ow2-asm",
        "@external_deps//:com_google_inject_guice_classes",
    ],
    runtime_deps = [":aopalliance"],
)

java_library(
    name = "guice-assistedinject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_inject_extensions_guice_assistedinject"],
    runtime_deps = [":guice"],
)

java_library(
    name = "guice-servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_inject_extensions_guice_servlet"],
    runtime_deps = [":guice"],
)
