java_binary(
    name = "ProtoGen",
    srcs = ["ProtoGen.java"],
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/proto"],
    visibility = ["//proto:__pkg__"],
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:args4j",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
