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
        "@maven_deps//auto-common/jar",
        "@maven_deps//auto-factory/jar",
        "@maven_deps//auto-service-annotations/jar",
        "@maven_deps//auto-value-annotations/jar",
        "@maven_deps//auto-value-gson-extension/jar",
        "@maven_deps//auto-value-gson-factory/jar",
        "@maven_deps//auto-value-gson-runtime/jar",
        "@maven_deps//autotransient/jar",
        "@maven_deps//gson/jar",
        "@maven_deps//guava/jar",
        "@maven_deps//javapoet/jar",
        "@maven_deps//javax_inject/jar",
    ],
)
