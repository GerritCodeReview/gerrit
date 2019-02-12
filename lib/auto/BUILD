java_plugin(
    name = "auto-annotation-plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value//jar",
    ],
)

java_plugin(
    name = "auto-factory-plugin",
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    deps = [
        "//lib:guava",
        "//lib/guice:javax_inject",
        "@auto-common//jar",
        "@auto-factory//jar",
        "@googlejavaformat//jar",
        "@java-poet//jar",
        "@javac-shaded//jar",
    ],
)

java_library(
    name = "auto-factory",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-factory-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@auto-factory//jar",
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

java_library(
    name = "auto-value",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-annotation-plugin",
        ":auto-value-plugin",
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
    ],
    visibility = ["//visibility:public"],
    exports = ["@auto-value-annotations//jar"],
)
