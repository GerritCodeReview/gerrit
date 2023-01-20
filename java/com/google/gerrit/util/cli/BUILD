load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "cli",
    srcs = glob(
        ["**/*.java"],
        exclude = ["ApiProtocolBufferGenerator.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:args4j",
        "//lib:guava",
        "//lib/auto:auto-value-annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)

# Util to generate *.proto files from *Info and *Input objects
java_binary(
    name = "protogen",
    srcs = ["ApiProtocolBufferGenerator.java"],
    main_class = "com.google.gerrit.util.cli.ApiProtocolBufferGenerator",
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib:protobuf",
    ],
)
