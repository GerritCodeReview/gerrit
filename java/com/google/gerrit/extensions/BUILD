load("//lib:guava.bzl", "GUAVA_DOC_URL")
load("//lib/jgit:jgit.bzl", "JGIT_DOC_URL")
load("//tools/bzl:gwt.bzl", "gwt_module")

EXT_API_SRCS = glob(["client/*.java"])

gwt_module(
    name = "client",
    srcs = EXT_API_SRCS,
    gwt_xml = "Extensions.gwt.xml",
    visibility = ["//visibility:public"],
)

java_binary(
    name = "extension-api",
    main_class = "Dummy",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_library(
    name = "lib",
    visibility = ["//visibility:public"],
    # Changes here should be reflected in com.google.gerrit.launcher.GerritLauncher#libClassLoader
    exports = [
        ":api",
        "//lib:guava",
        "//lib:servlet-api-3_1",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
    ],
)

#TODO(davido): There is no provided_deps argument to java_library rule
java_library(
    name = "api",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)

load("//tools/bzl:javadoc.bzl", "java_doc")

java_doc(
    name = "extension-api-javadoc",
    external_docs = [
        JGIT_DOC_URL,
        GUAVA_DOC_URL,
    ],
    libs = [":api"],
    pkgs = ["com.google.gerrit.extensions"],
    title = "Gerrit Review Extension API Documentation",
    visibility = ["//visibility:public"],
)
