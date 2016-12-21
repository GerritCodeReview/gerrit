java_library(
    name = "powermock-module-junit4",
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-module-junit4-common",
        "//lib:junit",
        "@powermock_module_junit4//jar",
    ],
)

java_library(
    name = "powermock-module-junit4-common",
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-reflect",
        "//lib:junit",
        "@powermock_module_junit4_common//jar",
    ],
)

java_library(
    name = "powermock-reflect",
    visibility = ["//visibility:public"],
    exports = [
        "//lib:junit",
        "//lib/easymock:objenesis",
        "@powermock_reflect//jar",
    ],
)

java_library(
    name = "powermock-api-easymock",
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-api-support",
        "//lib/easymock",
        "@powermock_api_easymock//jar",
    ],
)

java_library(
    name = "powermock-api-support",
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-core",
        ":powermock-reflect",
        "//lib:junit",
        "@powermock_api_support//jar",
    ],
)

java_library(
    name = "powermock-core",
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-reflect",
        "//lib:javassist",
        "//lib:junit",
    ],
)
