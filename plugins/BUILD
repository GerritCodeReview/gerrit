load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:javadoc.bzl", "java_doc")
load(
    "//tools/bzl:plugins.bzl",
    "CORE_PLUGINS",
    "CUSTOM_PLUGINS",
)

genrule2(
    name = "core",
    srcs = ["//plugins/%s:%s.jar" % (n, n) for n in CORE_PLUGINS + CUSTOM_PLUGINS],
    outs = ["core.zip"],
    cmd = "mkdir -p $$TMP/WEB-INF/plugins;" +
          "for s in $(SRCS) ; do " +
          "ln -s $$ROOT/$$s $$TMP/WEB-INF/plugins;done;" +
          "cd $$TMP;" +
          "zip -qr $$ROOT/$@ .",
    visibility = ["//visibility:public"],
)

PLUGIN_API = [
    "//java/com/google/gerrit/server",
    "//java/com/google/gerrit/server/ioutil",
    "//java/com/google/gerrit/server/restapi",
    "//java/com/google/gerrit/pgm/init/api",
    "//java/com/google/gerrit/httpd",
    "//java/com/google/gerrit/sshd",
]

EXPORTS = [
    "//antlr3:query_parser",
    "//java/com/google/gerrit/common:annotations",
    "//java/com/google/gerrit/common:server",
    "//java/com/google/gerrit/exceptions",
    "//java/com/google/gerrit/extensions:api",
    "//java/com/google/gerrit/git",
    "//java/com/google/gerrit/index",
    "//java/com/google/gerrit/index/project",
    "//java/com/google/gerrit/index:query_exception",
    "//java/com/google/gerrit/json",
    "//java/com/google/gerrit/lifecycle",
    "//java/com/google/gerrit/lucene",
    "//java/com/google/gerrit/mail",
    "//java/com/google/gerrit/metrics",
    "//java/com/google/gerrit/metrics/dropwizard",
    "//java/com/google/gerrit/entities",
    "//java/com/google/gerrit/server/api",
    "//java/com/google/gerrit/server/audit",
    "//java/com/google/gerrit/server/cache/mem",
    "//java/com/google/gerrit/server/cache/serialize",
    "//java/com/google/gerrit/server/data",
    "//java/com/google/gerrit/server/logging",
    "//java/com/google/gerrit/server/schema",
    "//java/com/google/gerrit/server/util/time",
    "//java/com/google/gerrit/util/cli",
    "//java/com/google/gerrit/util/http",
    "//java/com/google/gerrit/util/logging",
    "//lib/antlr:java-runtime",
    "//lib/auto:auto-value-annotations",
    "//lib/commons:compress",
    "//lib/commons:dbcp",
    "//lib/commons:lang",
    "//lib/dropwizard:dropwizard-core",
    "//lib/flogger:api",
    "//lib/guice:guice",
    "//lib/guice:guice-assistedinject",
    "//lib/guice:guice-servlet",
    "//lib/guice:javax_inject",
    "//lib/httpcomponents:httpclient",
    "//lib/httpcomponents:httpcore",
    "//lib/jackson:jackson-core",
    "//lib:jgit-servlet",
    "//lib:jgit",
    "//lib:jsr305",
    "//lib/log:api",
    "//lib/log:log4j",
    "//lib/mina:sshd",
    "//lib/ow2:ow2-asm",
    "//lib/ow2:ow2-asm-analysis",
    "//lib/ow2:ow2-asm-commons",
    "//lib/ow2:ow2-asm-util",
    "//lib:args4j",
    "//lib:blame-cache",
    "//lib:guava",
    "//lib:guava-retrying",
    "//lib:gson",
    "//lib:icu4j",
    "//lib:mime-util",
    "//lib:protobuf",
    "//lib:servlet-api-without-neverlink",
    "//lib:soy",
    "//prolog:gerrit-prolog-common",
]

java_binary(
    name = "bouncycastle-deploy-env",
    main_class = "Dummy",
    runtime_deps = [
        "//lib/bouncycastle:bcpg",
        "//lib/bouncycastle:bcpkix",
        "//lib/bouncycastle:bcprov",
    ],
)

java_binary(
    name = "plugin-api",
    deploy_env = ["bouncycastle-deploy-env"],
    main_class = "Dummy",
    visibility = ["//visibility:public"],
    runtime_deps = [":plugin-lib"],
)

java_library(
    name = "plugin-lib",
    visibility = ["//visibility:public"],
    exports = PLUGIN_API + EXPORTS,
)

java_library(
    name = "plugin-lib-neverlink",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_API + EXPORTS,
)

java_binary(
    name = "plugin-api-sources",
    main_class = "Dummy",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//antlr3:libquery_parser-src.jar",
        "//java/com/google/gerrit/common:libannotations-src.jar",
        "//java/com/google/gerrit/common:libserver-src.jar",
        "//java/com/google/gerrit/entities:libentities-src.jar",
        "//java/com/google/gerrit/extensions:libapi-src.jar",
        "//java/com/google/gerrit/httpd:libhttpd-src.jar",
        "//java/com/google/gerrit/index:libindex-src.jar",
        "//java/com/google/gerrit/index:libquery_exception-src.jar",
        "//java/com/google/gerrit/pgm/init/api:libapi-src.jar",
        "//java/com/google/gerrit/server:libserver-src.jar",
        "//java/com/google/gerrit/server/restapi:librestapi-src.jar",
        "//java/com/google/gerrit/sshd:libsshd-src.jar",
        "//java/com/google/gerrit/util/http:libhttp-src.jar",
    ],
)

java_doc(
    name = "plugin-api-javadoc",
    libs = PLUGIN_API + [
        "//antlr3:query_parser",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/util/http",
    ],
    pkgs = ["com.google.gerrit"],
    title = "Gerrit Review Plugin API Documentation",
    visibility = ["//visibility:public"],
)
