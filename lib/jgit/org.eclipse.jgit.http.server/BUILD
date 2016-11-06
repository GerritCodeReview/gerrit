java_library(
    name = "jgit-servlet",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [":jgit-servlet-alias"],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)

alias(
    name = "jgit-servlet-alias",
    actual = select({
#        "@//lib:jgit-dev": "@jgit//org.eclipse.jgit.http.server:jgit-servlet",
        "//conditions:default": "@jgit_servlet//jar",
    }),
    visibility = ["//visibility:public"],
)
