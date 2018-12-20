load("//tools/bzl:pkg_war.bzl", "LIBS", "PGMLIBS")
load("//tools/bzl:classpath.bzl", "classpath_collector")
load(
    "//tools/bzl:plugins.bzl",
    "CORE_PLUGINS",
    "CUSTOM_PLUGINS",
    "CUSTOM_PLUGINS_TEST_DEPS",
)

TEST_DEPS = [
    "//gerrit-gwtui:ui_tests",
    "//javatests/com/google/gerrit/elasticsearch:elasticsearch_test_utils",
    "//javatests/com/google/gerrit/server:server_tests",
]

DEPS = [
    "//gerrit-gwtdebug:gwtdebug",
    "//gerrit-gwtui:ui_module",
    "//gerrit-plugin-gwtui:gwtui-api-lib",
    "//java/com/google/gerrit/acceptance:lib",
    "//java/com/google/gerrit/server",
    "//java/com/google/gerrit/asciidoctor:asciidoc_lib",
    "//java/com/google/gerrit/asciidoctor:doc_indexer_lib",
    "//lib/auto:auto-value",
    "//lib/gwt:ant",
    "//lib/gwt:colt",
    "//lib/gwt:javax-validation",
    "//lib/gwt:javax-validation_src",
    "//lib/gwt:jsinterop-annotations",
    "//lib/gwt:jsinterop-annotations_src",
    "//lib/gwt:tapestry",
    "//lib/gwt:w3c-css-sac",
    "//lib/jetty:servlets",
    "//lib/prolog:compiler-lib",
    "//proto:reviewdb_java_proto",
]

java_library(
    name = "classpath",
    testonly = True,
    runtime_deps = LIBS + PGMLIBS + DEPS,
)

classpath_collector(
    name = "main_classpath_collect",
    testonly = True,
    deps = LIBS + PGMLIBS + DEPS + TEST_DEPS +
           ["//plugins/%s:%s__plugin" % (n, n) for n in CORE_PLUGINS + CUSTOM_PLUGINS] +
           ["//plugins/%s:%s__plugin_test_deps" % (n, n) for n in CUSTOM_PLUGINS_TEST_DEPS],
)

classpath_collector(
    name = "gwt_classpath_collect",
    deps = ["//gerrit-gwtui:ui_module"],
)

classpath_collector(
    name = "autovalue_classpath_collect",
    deps = ["//lib/auto:auto-value"],
)
