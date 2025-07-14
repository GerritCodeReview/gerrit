load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//tools/bzl:javadoc.bzl", "java_doc")

_DOC_VERS = "6.1.0.202203080745-r"

JGIT_DOC_URL = "https://archive.eclipse.org/jgit/site/" + _DOC_VERS + "/apidocs"

GUAVA_VERSION = "33.5.0-jre"

GUAVA_DOC_URL = "https://guava.dev/releases/" + GUAVA_VERSION + "/api/docs/"

java_binary(
    name = "extension-api",
    main_class = "Dummy",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_library(
    name = "lib",
    visibility = ["//visibility:public"],
    exports = [
        ":api",
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
    ],
)

java_library(
    name = "api",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/auto:auto-value-annotations",
        "//lib/errorprone:annotations",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)

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
