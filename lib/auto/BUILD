load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

java_plugin(
    name = "auto-annotation-plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        "@nongoogle_deps//:com_google_auto_value_auto_value_annotations",
        "@nongoogle_deps//:com_google_auto_value_auto_value",
    ],
)

java_plugin(
    name = "auto-factory-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    visibility = ["//visibility:private"],
    deps = [
        "@nongoogle_deps//:com_google_auto_auto_common",
        "@nongoogle_deps//:com_google_auto_factory_auto_factory",
        "@nongoogle_deps//:com_google_auto_service_auto_service_annotations",
        "@nongoogle_deps//:com_google_auto_value_auto_value_annotations",
        "@nongoogle_deps//:com_google_auto_value_auto_value",
        "@nongoogle_deps//:com_google_guava_guava",
        "@javapoet//jar",
        "@javax_inject//jar",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@nongoogle_deps//:com_google_auto_value_auto_value_annotations",
        "@nongoogle_deps//:com_google_auto_value_auto_value",
    ],
)

java_plugin(
    name = "auto-oneof-plugin",
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = [
        "@nongoogle_deps//:com_google_auto_value_auto_value_annotations",
        "@nongoogle_deps//:com_google_auto_value_auto_value",
    ],
)

java_plugin(
    name = "auto-value-gson-plugin",
    processor_class = "com.ryanharter.auto.value.gson.factory.AutoValueGsonAdapterFactoryProcessor",
    deps = [
        "@nongoogle_deps//:com_google_auto_value_auto_value_annotations",
        "@auto-value-gson-extension//jar",
        "@auto-value-gson-factory//jar",
        "@auto-value-gson-runtime//jar",
        "@nongoogle_deps//:com_google_auto_value_auto_value",
        "@autotransient//jar",
        "@nongoogle_deps//:com_google_code_gson_gson",
        "@javapoet//jar",
    ],
)

java_library(
    name = "auto-factory",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-factory-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@nongoogle_deps//:com_google_auto_factory_auto_factory"],
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
    exports = ["@nongoogle_deps//:com_google_auto_value_auto_value"],
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
    exports = ["@nongoogle_deps//:com_google_auto_value_auto_value_annotations"],
)

java_library(
    name = "auto-value-gson",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-value-gson-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@auto-value-gson-runtime//jar",
    ],
)
