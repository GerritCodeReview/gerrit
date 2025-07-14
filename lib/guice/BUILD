load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "guice",
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
    exports = ["@gerrit_deps//:com_google_inject_guice"],
    runtime_deps = ["aopalliance"],
)

java_library(
    name = "guice-assistedinject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit_deps//:com_google_inject_extensions_guice_assistedinject"],
    runtime_deps = [":guice"],
)

java_library(
    name = "guice-servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit_deps//:com_google_inject_extensions_guice_servlet"],
    runtime_deps = [":guice"],
)

java_library(
    name = "aopalliance",
    data = ["//lib:LICENSE-PublicDomain"],
    exports = ["@gerrit_deps//:aopalliance_aopalliance"],
)

java_library(
    name = "jakarta-inject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit_deps//:jakarta_inject_jakarta_inject_api"],
)

java_library(
    name = "javax_inject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit_deps//:javax_inject_javax_inject"],
)
