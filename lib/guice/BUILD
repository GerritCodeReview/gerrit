load("@rules_java//java:defs.bzl", "java_library")

# Flavour-aware Guice. The whole Gerrit tree depends on //lib/guice:guice, so a
# select() here flips every consumer to Guice 7 (jakarta.inject) under
# --define=flavour=ee11 with no per-target change. Default (no define) is the
# unchanged Guice 6 cluster, so the EE8 release is byte-identical. Same pattern
# for :guice-servlet and :guice-assistedinject below.
java_library(
    name = "guice",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = select({
        "//tools:ee11": [":guice-ee11"],
        "//conditions:default": [":guice-ee8"],
    }),
)

java_library(
    name = "guice-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guice-library",
        ":jakarta-inject",
        ":javax_inject",
    ],
)

java_library(
    name = "guice-library",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "//lib/ow2:ow2-asm",
        "@external_deps//:com_google_inject_guice_classes",
    ],
    runtime_deps = ["aopalliance"],
)

java_library(
    name = "guice-assistedinject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = select({
        "//tools:ee11": [":guice-assistedinject-ee11"],
        "//conditions:default": [":guice-assistedinject-ee8"],
    }),
)

java_library(
    name = "guice-assistedinject-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_inject_extensions_guice_assistedinject"],
    runtime_deps = [":guice"],
)

java_library(
    name = "guice-servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = select({
        "//tools:ee11": [":guice-servlet-ee11"],
        "//conditions:default": [":guice-servlet-ee8"],
    }),
)

java_library(
    name = "guice-servlet-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_inject_extensions_guice_servlet"],
    runtime_deps = [":guice"],
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
# javax.inject), so guice-ee11 exports jakarta-inject only. The default targets
# above (Guice 6, javax.inject) are unchanged and feed the EE8/default WAR.
java_library(
    name = "guice-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guice-library-ee11",
        ":jakarta-inject-ee11",
    ],
)

java_library(
    name = "guice-library-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps_ee11//:com_google_inject_guice"],
    runtime_deps = [":aopalliance-ee11"],
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

java_library(
    name = "aopalliance-ee11",
    data = ["//lib:LICENSE-PublicDomain"],
    exports = ["@external_deps_ee11//:aopalliance_aopalliance"],
)

java_library(
    name = "jakarta-inject-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps_ee11//:jakarta_inject_jakarta_inject_api"],
)
