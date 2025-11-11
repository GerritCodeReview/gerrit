load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

java_plugin(
    name = "auto-annotation-plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        "@maven_deps//auto-value-annotations/jar",
        "@maven_deps//auto-value/jar",
    ],
)

java_plugin(
    name = "auto-factory-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    visibility = ["//visibility:private"],
    deps = [
        "@maven_deps//auto-common/jar",
        "@maven_deps//auto-factory/jar",
        "@maven_deps//auto-service-annotations/jar",
        "@maven_deps//auto-value-annotations/jar",
        "@maven_deps//auto-value/jar",
        "@maven_deps//guava/jar",
        "@maven_deps//javapoet/jar",
        "@maven_deps//javax_inject/jar",
    ],
)

java_plugin(
    name = "auto-builder-plugin",
    processor_class = "com.google.auto.value.processor.AutoBuilderProcessor",
    deps = [
        "@maven_deps//auto-common/jar",
        "@maven_deps//auto-factory/jar",
        "@maven_deps//auto-service-annotations/jar",
        "@maven_deps//auto-value-annotations/jar",
        "@maven_deps//auto-value/jar",
        "@maven_deps//guava/jar",
        "@maven_deps//javapoet/jar",
        "@maven_deps//javax_inject/jar",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@maven_deps//auto-value-annotations/jar",
        "@maven_deps//auto-value/jar",
    ],
)

java_plugin(
    name = "auto-oneof-plugin",
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = [
        "@maven_deps//auto-value-annotations/jar",
        "@maven_deps//auto-value/jar",
    ],
)

java_plugin(
    name = "auto-value-gson-plugin",
    processor_class = "com.ryanharter.auto.value.gson.factory.AutoValueGsonAdapterFactoryProcessor",
    deps = [
        "@maven_deps//auto-value-annotations/jar",
        "@maven_deps//auto-value-gson-extension/jar",
        "@maven_deps//auto-value-gson-factory/jar",
        "@maven_deps//auto-value-gson-runtime/jar",
        "@maven_deps//auto-value/jar",
        "@maven_deps//autotransient/jar",
        "@maven_deps//gson/jar",
        "@maven_deps//javapoet/jar",
    ],
)

java_library(
    name = "auto-factory",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-factory-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//auto-factory/jar"],
)

java_library(
    name = "auto-value",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-annotation-plugin",
        ":auto-builder-plugin",
        ":auto-value-plugin",
        ":auto-oneof-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//auto-value/jar"],
)

java_library(
    name = "auto-value-annotations",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-annotation-plugin",
        ":auto-builder-plugin",
        ":auto-value-plugin",
        ":auto-oneof-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//auto-value-annotations/jar"],
)

java_library(
    name = "auto-value-gson",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-value-gson-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@maven_deps//auto-value-gson-runtime/jar",
    ],
)
