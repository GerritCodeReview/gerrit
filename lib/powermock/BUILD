java_library(
    name = "powermock-module-junit4",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-module-junit4-common",
        "//lib:junit",
        "@powermock_module_junit4//jar",
    ],
)

java_library(
    name = "powermock-module-junit4-common",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-reflect",
        "//lib:junit",
        "@powermock_module_junit4_common//jar",
    ],
)

java_library(
    name = "powermock-reflect",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        "//lib:junit",
        "//lib/easymock:objenesis",
        "@powermock_reflect//jar",
    ],
)

java_library(
    name = "powermock-api-easymock",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-api-support",
        "//lib/easymock",
        "@powermock_api_easymock//jar",
    ],
)

java_library(
    name = "powermock-api-support",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
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
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-reflect",
        "//lib:javassist",
        "//lib:junit",
    ],
)
