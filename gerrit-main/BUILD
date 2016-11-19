java_binary(
    name = "main_bin",
    main_class = "Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":main_lib"],
)

java_library(
    name = "main_lib",
    srcs = ["src/main/java/Main.java"],
    visibility = ["//visibility:public"],
    deps = ["//gerrit-launcher:launcher"],
)
