load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "truth",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit_deps//:com_google_truth_truth"],
    runtime_deps = [
        ":diffutils",
        "//lib:guava",
        "//lib:junit",
    ],
)

java_library(
    name = "truth-java8-extension",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit_deps//:com_google_truth_extensions_truth_java8_extension"],
    runtime_deps = [
        ":truth",
        "//lib:guava",
    ],
)

java_library(
    name = "truth-liteproto-extension",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:private"],
    exports = ["@gerrit_deps//:com_google_truth_extensions_truth_liteproto_extension"],
    runtime_deps = [
        ":truth",
        "//lib:guava",
        "//lib:protobuf",
    ],
)

java_library(
    name = "diffutils",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:private"],
    exports = ["@gerrit_deps//:io_github_java_diff_utils_java_diff_utils"],
)

java_library(
    name = "truth-proto-extension",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":truth-liteproto-extension",
        "@gerrit_deps//:com_google_truth_extensions_truth_proto_extension",
    ],
    runtime_deps = [
        ":truth",
        ":truth-liteproto-extension",
        "//lib:guava",
        "//lib:protobuf",
    ],
)
