load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

java_plugin(
    name = "auto-annotation-plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value//jar",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value//jar",
    ],
)

java_plugin(
    name = "auto-oneof-plugin",
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value//jar",
    ],
)

java_plugin(
    name = "auto-value-gson-plugin",
    processor_class = "com.ryanharter.auto.value.gson.factory.AutoValueGsonAdapterFactoryProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value-gson-extension//jar",
        "@auto-value-gson-factory//jar",
        "@auto-value-gson-runtime//jar",
        "@auto-value//jar",
        "@autotransient//jar",
        "@gson//jar",
        "@javapoet//jar",
    ],
)

java_library(
    name = "auto-value",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-annotation-plugin",
        ":auto-value-plugin",
        ":auto-oneof-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@auto-value//jar"],
)

java_library(
    name = "auto-value-annotations",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-annotation-plugin",
        ":auto-value-plugin",
        ":auto-oneof-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@auto-value-annotations//jar"],
)

java_library(
    name = "auto-value-gson",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-value-gson-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@auto-value-gson-extension//jar",
        "@auto-value-gson-factory//jar",
        "@auto-value-gson-runtime//jar",
    ],
)
