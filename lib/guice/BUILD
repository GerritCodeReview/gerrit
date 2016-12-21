java_library(
    name = "guice",
    visibility = ["//visibility:public"],
    exports = [
        ":guice_library",
        ":javax-inject",
        ":multibindings",
    ],
)

java_library(
    name = "guice_library",
    visibility = ["//visibility:public"],
    exports = ["@guice_library//jar"],
    runtime_deps = ["aopalliance"],
)

java_library(
    name = "guice-assistedinject",
    visibility = ["//visibility:public"],
    exports = ["@guice_assistedinject//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "guice-servlet",
    visibility = ["//visibility:public"],
    exports = ["@guice_servlet//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "aopalliance",
    exports = ["@aopalliance//jar"],
)

java_library(
    name = "javax-inject",
    visibility = ["//visibility:public"],
    exports = ["@javax_inject//jar"],
)

java_library(
    name = "multibindings",
    visibility = ["//visibility:public"],
    exports = ["@multibindings//jar"],
)
