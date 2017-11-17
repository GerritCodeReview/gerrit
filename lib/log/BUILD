java_library(
    name = "api",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@log_api//jar"],
)

java_library(
    name = "nop",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@log_nop//jar"],
    runtime_deps = [":api"],
)

java_library(
    name = "impl_log4j",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@impl_log4j//jar"],
    runtime_deps = [
      ":log4j1.2-api",
    ],
)

java_library(
    name = "jcl-over-slf4j",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@jcl_over_slf4j//jar"],
)

java_library(
    name = "log4j-api",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@log4j-api//jar"],
)

java_library(
    name = "log4j-core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@log4j-core//jar"],
)

java_library(
    name = "log4j1.2-api",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@log4j1.2-api//jar"],
)

java_library(
    name = "jsonevent-layout",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jsonevent_layout//jar"],
    runtime_deps = [
        ":json-smart",
        "//lib/commons:lang",
    ],
)

java_library(
    name = "json-smart",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@json_smart//jar"],
)
