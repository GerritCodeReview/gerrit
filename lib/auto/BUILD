load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

java_plugin(
    name = "auto-annotation-plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        "@external_deps//:com_google_auto_value_auto_value",
        "@external_deps//:com_google_auto_value_auto_value_annotations",
    ],
)

java_plugin(
    name = "auto-factory-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    visibility = ["//visibility:private"],
    deps = [
        "@external_deps//:com_google_auto_auto_common",
        "@external_deps//:com_google_auto_factory_auto_factory",
        "@external_deps//:com_google_auto_service_auto_service_annotations",
        "@external_deps//:com_google_auto_value_auto_value",
        "@external_deps//:com_google_auto_value_auto_value_annotations",
        "@external_deps//:com_google_guava_guava",
        "@external_deps//:com_squareup_javapoet",
        "@external_deps//:javax_inject_javax_inject",
    ],
)

java_plugin(
    name = "auto-builder-plugin",
    processor_class = "com.google.auto.value.processor.AutoBuilderProcessor",
    deps = [
        "@external_deps//:com_google_auto_auto_common",
        "@external_deps//:com_google_auto_factory_auto_factory",
        "@external_deps//:com_google_auto_service_auto_service_annotations",
        "@external_deps//:com_google_auto_value_auto_value",
        "@external_deps//:com_google_auto_value_auto_value_annotations",
        "@external_deps//:com_google_guava_guava",
        "@external_deps//:com_squareup_javapoet",
        "@external_deps//:javax_inject_javax_inject",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@external_deps//:com_google_auto_value_auto_value",
        "@external_deps//:com_google_auto_value_auto_value_annotations",
    ],
)

java_plugin(
    name = "auto-oneof-plugin",
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = [
        "@external_deps//:com_google_auto_value_auto_value",
        "@external_deps//:com_google_auto_value_auto_value_annotations",
    ],
)

java_plugin(
    name = "auto-value-gson-plugin",
    processor_class = "com.ryanharter.auto.value.gson.factory.AutoValueGsonAdapterFactoryProcessor",
    deps = [
        "@external_deps//:com_google_auto_value_auto_value",
        "@external_deps//:com_google_auto_value_auto_value_annotations",
        "@external_deps//:com_google_code_gson_gson",
        "@external_deps//:com_ryanharter_auto_value_auto_value_gson_extension",
        "@external_deps//:com_ryanharter_auto_value_auto_value_gson_factory",
        "@external_deps//:com_ryanharter_auto_value_auto_value_gson_runtime",
        "@external_deps//:com_squareup_javapoet",
        "@external_deps//:io_sweers_autotransient_autotransient",
    ],
)

java_library(
    name = "auto-factory",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-factory-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_auto_factory_auto_factory"],
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
    exports = ["@external_deps//:com_google_auto_value_auto_value"],
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
    exports = ["@external_deps//:com_google_auto_value_auto_value_annotations"],
)

java_library(
    name = "auto-value-gson",
    data = ["//lib:LICENSE-Apache2.0"],
    exported_plugins = [
        ":auto-value-gson-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@external_deps//:com_ryanharter_auto_value_auto_value_gson_runtime",
    ],
)
