load("@rules_java//java:defs.bzl", "java_library")

# Flavour-aware Guice. The whole Gerrit tree depends on //lib/guice:guice, so a
# select() here flips every consumer to Guice 7 (jakarta.inject) under
# --@com_googlesource_gerrit_bazlets//flags:flavour=ee10 with no per-target change. The default (no flag,
# ee8) is the unchanged Guice 6 cluster, so the EE8 release is byte-identical. Same pattern
# for :guice-servlet and :guice-assistedinject below.
java_library(
    name = "guice",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = select({
        "//tools:ee10": [":guice-ee10"],
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
    exports = ["@external_deps//:com_google_inject_guice"],
    runtime_deps = ["aopalliance"],
)

java_library(
    name = "guice-assistedinject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = select({
        "//tools:ee10": [":guice-assistedinject-ee10"],
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
        "//tools:ee10": [":guice-servlet-ee10"],
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

# Guice 7 cluster for the EE10 (jakarta.servlet) release flavour, resolved in the
# isolated external_deps_ee10 repository. Guice 7 uses jakarta.inject (not
# javax.inject), so guice-ee10 exports jakarta-inject only. The default targets
# above (Guice 6, javax.inject) are unchanged and feed the EE8/default WAR.
java_library(
    name = "guice-ee10",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guice-library-ee10",
        ":jakarta-inject-ee10",
    ],
)

java_library(
    name = "guice-library-ee10",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps_ee10//:com_google_inject_guice"],
    runtime_deps = [":aopalliance-ee10"],
)

java_library(
    name = "guice-assistedinject-ee10",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps_ee10//:com_google_inject_extensions_guice_assistedinject"],
    runtime_deps = [":guice-ee10"],
)

java_library(
    name = "guice-servlet-ee10",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps_ee10//:com_google_inject_extensions_guice_servlet"],
    runtime_deps = [":guice-ee10"],
)

java_library(
    name = "aopalliance-ee10",
    data = ["//lib:LICENSE-PublicDomain"],
    exports = ["@external_deps_ee10//:aopalliance_aopalliance"],
)

java_library(
    name = "jakarta-inject-ee10",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps_ee10//:jakarta_inject_jakarta_inject_api"],
)
