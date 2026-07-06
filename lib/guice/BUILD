load("@com_googlesource_gerrit_bazlets//tools:servlet_flavour.bzl", "servlet_flavour_deps")
load("@rules_java//java:defs.bzl", "java_library")

# Flavour-aware Guice labels. The whole Gerrit tree depends on these neutral
# labels, and the servlet-flavour toolchain resolves them to the matching Guice
# cluster: Guice 6 under EE8/default, Guice 7 under the EE11 flavour.
servlet_flavour_deps(
    name = "guice",
    data = ["//lib:LICENSE-Apache2.0"],
    license_deps = [
        ":guice-ee11",
        ":guice-ee8",
    ],
    part = "guice",
    visibility = ["//visibility:public"],
)

java_library(
    name = "guice-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guice-library-ee8",
        ":jakarta-inject",
        ":javax_inject",
    ],
)

java_library(
    name = "guice-library-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "//lib/ow2:ow2-asm",
        "@external_deps//:com_google_inject_guice_classes",
    ],
    runtime_deps = ["aopalliance"],
)

servlet_flavour_deps(
    name = "guice-assistedinject",
    data = ["//lib:LICENSE-Apache2.0"],
    license_deps = [
        ":guice-assistedinject-ee11",
        ":guice-assistedinject-ee8",
    ],
    part = "guice_assistedinject",
    visibility = ["//visibility:public"],
)

java_library(
    name = "guice-assistedinject-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_inject_extensions_guice_assistedinject"],
    runtime_deps = [":guice-ee8"],
)

servlet_flavour_deps(
    name = "guice-servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    license_deps = [
        ":guice-servlet-ee11",
        ":guice-servlet-ee8",
    ],
    part = "guice_servlet",
    visibility = ["//visibility:public"],
)

java_library(
    name = "guice-servlet-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_inject_extensions_guice_servlet"],
    runtime_deps = [":guice-ee8"],
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

java_library(
    name = "javax_inject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:javax_inject_javax_inject"],
)

# Guice 7 cluster for the EE11 (jakarta.servlet) release flavour, resolved in the
# isolated external_deps_ee11 repository. Guice 7 uses jakarta.inject (not
# javax.inject), so guice-ee11 exports jakarta-inject (the EE8 cluster also
# exports javax_inject). aopalliance and jakarta-inject are the same version in
# both repos, so they are shared as neutral targets rather than mirrored -ee11.
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
        "@external_deps_ee11//:com_google_inject_guice_classes",
    ],
    runtime_deps = [":aopalliance"],
)

java_library(
    name = "guice-assistedinject-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps_ee11//:com_google_inject_extensions_guice_assistedinject"],
    runtime_deps = [":guice-ee11"],
)

java_library(
    name = "guice-servlet-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps_ee11//:com_google_inject_extensions_guice_servlet"],
    runtime_deps = [":guice-ee11"],
)
