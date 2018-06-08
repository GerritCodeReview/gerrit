java_library(
    name = "powermock-module-junit4",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-module-junit4-common",
        "//lib:junit",
        "@powermock-module-junit4//jar",
    ],
)

java_library(
    name = "powermock-module-junit4-common",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-reflect",
        "//lib:junit",
        "@powermock-module-junit4-common//jar",
    ],
)

java_library(
    name = "powermock-reflect",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        "//lib:junit",
        "//lib/easymock:objenesis",
        "@powermock-reflect//jar",
    ],
)

java_library(
    name = "powermock-api-easymock",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":powermock-api-support",
        "//lib/easymock",
        "@powermock-api-easymock//jar",
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
        "@powermock-api-support//jar",
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
        "@powermock-core//jar",
    ],
)
