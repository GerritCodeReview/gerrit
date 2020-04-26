load("@rules_java//java:defs.bzl", "java_binary", "java_library")

java_binary(
    name = "gerrit-main-class",
    main_class = select({
        "//:java8": "MainJava8",
        "//:java11": "MainJava11",
        "//:java_next": "MainJavaNext",
        "//conditions:default": "MainJava8",
    }),
    visibility = ["//visibility:public"],
    runtime_deps = [":gerrit-main-class-lib"],
)

java_library(
    name = "gerrit-main-class-lib",
    srcs = ["Main.java"] + select({
        "//:java8": ["MainJava8.java"],
        "//:java11": ["MainJava11.java"],
        "//:java_next": ["MainJavaNext.java"],
        "//conditions:default": ["MainJava8.java"],
    }),
    deps = ["//java/com/google/gerrit/launcher"],
)
