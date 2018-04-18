java_plugin(
    name = "auto-annotation-plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        "@auto_value//jar",
        "@auto_value_annotations//jar",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@auto_value//jar",
        "@auto_value_annotations//jar",
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
    exports = ["@auto_value//jar"],
)

java_library(
    name = "auto-value-annotations",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-annotation-plugin",
        ":auto-value-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@auto_value_annotations//jar"],
)
