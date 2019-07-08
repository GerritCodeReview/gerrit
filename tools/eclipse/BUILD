load("//tools/bzl:classpath.bzl", "classpath_collector")
load("//tools/bzl:pkg_war.bzl", "LIBS", "PGMLIBS")
load(
    "//tools/bzl:plugins.bzl",
    "CORE_PLUGINS",
    "CUSTOM_PLUGINS",
    "CUSTOM_PLUGINS_TEST_DEPS",
)

TEST_DEPS = [
    "//gerrit-gpg:gpg_tests",
    "//gerrit-gwtui:ui_tests",
    "//gerrit-httpd:httpd_tests",
    "//gerrit-patch-jgit:jgit_patch_tests",
    "//gerrit-reviewdb:client_tests",
    "//gerrit-server:server_tests",
]

DEPS = [
    "//gerrit-acceptance-tests:lib",
    "//gerrit-gwtdebug:gwtdebug",
    "//gerrit-gwtui:ui_module",
    "//gerrit-main:main_lib",
    "//gerrit-plugin-gwtui:gwtui-api-lib",
    "//gerrit-server:server",
    "//lib/asciidoctor:asciidoc_lib",
    "//lib/asciidoctor:doc_indexer_lib",
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
    # TODO(davido): I do not understand why it must be on the Eclipse classpath
    #'//Documentation:index',
]

java_library(
    name = "classpath",
    testonly = 1,
    runtime_deps = LIBS + PGMLIBS + DEPS,
)

classpath_collector(
    name = "main_classpath_collect",
    testonly = 1,
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
