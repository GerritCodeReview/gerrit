load("@com_googlesource_gerrit_bazlets//tools:servlet_flavour.bzl", "servlet_flavour_deps")
load("@rules_java//java:defs.bzl", "java_library")

# Flavour-aware Guice labels. The whole Gerrit tree depends on these neutral
# labels, and the servlet-flavour toolchain resolves them to the flavour's
# Guice cluster -- Guice 7 (jakarta.inject) for EE11, the only flavour since
# the EE8 retirement.
servlet_flavour_deps(
    name = "guice",
    data = ["//lib:LICENSE-Apache2.0"],
    license_deps = [":guice-ee11"],
    part = "guice",
    visibility = ["//visibility:public"],
)

servlet_flavour_deps(
    name = "guice-assistedinject",
    data = ["//lib:LICENSE-Apache2.0"],
    license_deps = [":guice-assistedinject-ee11"],
    part = "guice_assistedinject",
    visibility = ["//visibility:public"],
)

servlet_flavour_deps(
    name = "guice-servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    license_deps = [":guice-servlet-ee11"],
    part = "guice_servlet",
    visibility = ["//visibility:public"],
)

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

# Guice 7 cluster for the EE11 (jakarta.servlet) flavour. Guice 7 uses
# jakarta.inject (not javax.inject), so guice-ee11 exports jakarta-inject.
java_library(
    name = "guice-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guice-library-ee11",
        ":jakarta-inject",
    ],
)

java_library(
    name = "guice-library-ee11",
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
    name = "guice-assistedinject-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_inject_extensions_guice_assistedinject"],
    runtime_deps = [":guice-ee11"],
)

java_library(
    name = "guice-servlet-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_inject_extensions_guice_servlet"],
    runtime_deps = [":guice-ee11"],
)
