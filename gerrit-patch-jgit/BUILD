java_library(
    name = "server",
    srcs = [
        "src/main/java/org/eclipse/jgit/" + x
        for x in [
            "diff/EditDeserializer.java",
            "diff/ReplaceEdit.java",
            "internal/storage/file/WindowCacheStatAccessor.java",
            "lib/ObjectIdSerialization.java",
        ]
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:gson",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
