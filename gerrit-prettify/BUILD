SRC = "src/main/java/com/google/gerrit/prettify/"

java_library(
    name = "server",
    srcs = glob([SRC + "common/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-patch-jgit:server",
        "//gerrit-reviewdb:server",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)

exports_files([
    "src/main/resources/com/google/gerrit/prettify/client/prettify.css",
    "src/main/resources/com/google/gerrit/prettify/client/prettify.js",
])
