java_library(
    name = "jgit-servlet",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = select({
        # "//lib/jgit:dev": ["@jgit//org.eclipse.jgit.http.server:jgit-servlet"],
        "//conditions:default": ["@jgit_servlet//jar"],
    }),
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
