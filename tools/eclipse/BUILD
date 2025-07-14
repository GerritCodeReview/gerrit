load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:classpath.bzl", "classpath_collector")
load("//tools/bzl:pkg_war.bzl", "LIBS", "PGMLIBS")
load(
    "//tools/bzl:plugins.bzl",
    "CORE_PLUGINS",
    "CUSTOM_PLUGINS",
    "CUSTOM_PLUGINS_TEST_DEPS",
)

TEST_DEPS = [
    "//javatests/com/google/gerrit/server:server_tests",
]

TEST_DEPS_GENERATED = [
    "//proto/testing:test_java_proto",
]

DEPS = [
    "//java/com/google/gerrit/acceptance:lib",
    "//java/com/google/gerrit/server",
    "//java/com/google/gerrit/asciidoctor:asciidoc_lib",
    "//java/com/google/gerrit/asciidoctor:doc_indexer_lib",
    "//lib/auto:auto-value",
    "//lib/prolog:compiler-lib",
    "//proto:entities_java_proto",
    "//proto:flow_java_proto",
]

java_library(
    name = "classpath",
    testonly = True,
    runtime_deps = LIBS + PGMLIBS + DEPS + TEST_DEPS_GENERATED,
)

classpath_collector(
    name = "main_classpath_collect",
    testonly = True,
    deps = LIBS + PGMLIBS + DEPS + TEST_DEPS + TEST_DEPS_GENERATED +
           ["//plugins/%s__plugin" % (n if ":" in n else "%s:%s" % (n, n)) for n in CORE_PLUGINS + CUSTOM_PLUGINS] +
           ["//plugins/%s:%s__plugin_test_deps" % (n, n) for n in CUSTOM_PLUGINS_TEST_DEPS],
)

classpath_collector(
    name = "autovalue_classpath_collect",
    deps = [
        "//lib/auto:auto-value",
        "@maven//:com_google_auto_auto_common",
        "@maven//:com_google_auto_factory_auto_factory",
        "@maven//:com_google_auto_service_auto_service_annotations",
        "@maven//:com_google_auto_value_auto_value_annotations",
        "@maven//:com_ryanharter_auto_value_auto_value_gson_extension",
        "@maven//:com_ryanharter_auto_value_auto_value_gson_factory",
        "@maven//:com_ryanharter_auto_value_auto_value_gson_runtime",
        "@maven//:io_sweers_autotransient_autotransient",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
        "@maven//:com_squareup_javapoet",
        "@maven//:javax_inject_javax_inject",
    ],
)
