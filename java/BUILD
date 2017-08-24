java_binary(
    name = "gerrit-main-class",
    main_class = "Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":gerrit-main-class-lib"],
)

java_library(
    name = "gerrit-main-class-lib",
    srcs = ["Main.java"],
    deps = ["//java/com/google/gerrit/launcher"],
)
