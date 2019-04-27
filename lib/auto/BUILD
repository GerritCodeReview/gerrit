java_plugin(
    name = "auto-annotation-plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value//jar",
        "@gson//jar",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value-gson-runtime//jar",
        "@auto-value-gson//jar",
        "@auto-value//jar",
        "@gson//jar",
        "@java-poet//jar",
    ],
)

java_plugin(
    name = "auto-value-gson-plugin",
    processor_class = "com.ryanharter.auto.value.gson.AutoValueGsonAdapterFactoryProcessor",
    deps = [
        ":auto-value-gson-jar",
        ":auto-value-gson-runtime",
        ":java-poet",
        ":sweers-autotransient",
    ],
)

java_library(
    name = "auto-value-gson",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-value-gson-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@auto-value-gson//jar"],
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

java_library(
    name = "auto-value-gson-jar",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@auto-value-gson//jar"],
)

java_library(
    name = "auto-value-gson-runtime",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@auto-value-gson-runtime//jar"],
)

java_library(
    name = "java-poet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@java-poet//jar"],
)

java_library(
    name = "sweers-autotransient",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@sweers-autotransient//jar"],
)
