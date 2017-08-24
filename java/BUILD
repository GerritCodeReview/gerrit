java_binary(
    name = "main_bin",
    main_class = "Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":main_lib"],
)

java_library(
    name = "main_lib",
    srcs = ["Main.java"],
    visibility = ["//visibility:public"],
    deps = ["//java/com/google/gerrit/launcher"],
)
