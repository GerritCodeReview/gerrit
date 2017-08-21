SRCS = glob(
    ["src/main/java/**/*.java"],
)

RESOURCES = glob(["src/main/resources/**/*"])

java_library(
    name = "oauth",
    srcs = SRCS,
    resources = RESOURCES,
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-httpd:httpd",
        "//gerrit-server:server",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:servlet-api-3_1",
        "//lib/commons:codec",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
    ],
)
