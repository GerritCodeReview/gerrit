load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//lib:guava.bzl", "GUAVA_DOC_URL")
load("//lib:jgit.bzl", "JGIT_DOC_URL")
load("//tools/bzl:javadoc.bzl", "java_doc")

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

#TODO(davido): There is no provided_deps argument to java_library rule
java_library(
    name = "api",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib/auto:auto-value-annotations",
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
