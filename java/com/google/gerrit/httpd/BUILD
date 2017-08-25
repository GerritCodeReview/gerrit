# TODO(davido): Consider to isolate war specific sources in its own package
WAR_SRCS = [
    "ReviewDbDataSourceProvider.java",
    "SiteInitializer.java",
    "SitePathFromSystemConfigProvider.java",
    "UnzippedDistribution.java",
    "WebAppInitializer.java",
]

DEPS = [
    "//java/com/google/gerrit/extensions:api",
    "//java/com/google/gerrit/lifecycle",
    "//java/com/google/gerrit/reviewdb:server",
    "//java/com/google/gerrit/server",
    "//java/com/google/gerrit/server/git/receive",
    "//lib:guava",
    "//lib:gwtorm",
    "//lib/guice",
    "//lib/guice:guice-servlet",
    "//lib/jgit/org.eclipse.jgit:jgit",
    "//lib/log:api",
]

java_library(
    name = "war-init",
    srcs = WAR_SRCS,
    visibility = ["//visibility:public"],
    deps = DEPS + [
        ":httpd",
        "//java/com/google/gerrit/elasticsearch",
        "//java/com/google/gerrit/gpg",
        "//java/com/google/gerrit/httpd/auth/oauth",
        "//java/com/google/gerrit/httpd/auth/openid",
        "//java/com/google/gerrit/lucene",
        "//java/com/google/gerrit/metrics/dropwizard",
        "//java/com/google/gerrit/pgm/init",
        "//java/com/google/gerrit/pgm/util",
        "//java/com/google/gerrit/server:module",
        "//java/com/google/gerrit/server/cache/h2",
        "//java/com/google/gerrit/sshd",
        "//lib:servlet-api-3_1",
        "//prolog:gerrit-prolog-common",
    ],
)

java_library(
    name = "httpd",
    srcs = glob(
        ["**/*.java"],
        exclude = WAR_SRCS,
    ),
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/httpd"],
    visibility = ["//visibility:public"],
    deps = DEPS + [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/launcher",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/util/cli",
        "//java/com/google/gerrit/util/http",
        "//java/com/google/gwtexpui/linker:server",
        "//java/com/google/gwtexpui/server",
        "//java/org/eclipse/jgit:server",
        "//lib:args4j",
        "//lib:gson",
        "//lib:gwtjsonrpc",
        "//lib:jsch",
        "//lib:servlet-api-3_1",
        "//lib:soy",
        "//lib/auto:auto-value",
        "//lib/commons:codec",
        "//lib/guice:guice-assistedinject",
        "//lib/jgit/org.eclipse.jgit.http.server:jgit-servlet",
    ],
)
