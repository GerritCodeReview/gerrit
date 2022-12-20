load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:javadoc.bzl", "java_doc")
load(
    "//tools/bzl:plugins.bzl",
    "CORE_PLUGINS",
    "CUSTOM_PLUGINS",
)
load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_test")

package(default_visibility = ["//visibility:public"])

exports_files([
    ".eslintrc.js",
    ".eslintignore",
    ".prettierrc.js",
    "rollup.config.js",
    "tsconfig-plugins-base.json",
])

genrule2(
    name = "core",
    srcs = ["//plugins/%s:%s.jar" % (n, n) for n in CORE_PLUGINS + CUSTOM_PLUGINS],
    outs = ["core.zip"],
    cmd = "mkdir -p $$TMP/WEB-INF/plugins;" +
          "for s in $(SRCS) ; do " +
          "ln -s $$ROOT/$$s $$TMP/WEB-INF/plugins;done;" +
          "cd $$TMP;" +
          "zip -qr $$ROOT/$@ .",
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
    "//java/com/google/gerrit/server/git/receive",
    "//java/com/google/gerrit/server/logging",
    "//java/com/google/gerrit/server/schema",
    "//java/com/google/gerrit/server/util/time",
    "//java/com/google/gerrit/proto",
    "//java/com/google/gerrit/util/cli",
    "//java/com/google/gerrit/util/http",
    "//java/com/google/gerrit/util/logging",
    "//lib/antlr:java-runtime",
    "//lib/auto:auto-value-annotations",
    "//lib/auto:auto-value-gson",
    "//lib/commons:compress",
    "//lib/commons:dbcp",
    "//lib/commons:lang3",
    "//lib/dropwizard:dropwizard-core",
    "//lib/flogger:api",
    "//lib/guice:guice",
    "//lib/guice:guice-assistedinject",
    "//lib/guice:guice-servlet",
    "//lib/guice:javax_inject",
    "//lib/httpcomponents:httpclient",
    "//lib/httpcomponents:httpcore",
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
    visibility = ["//visibility:private"],
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
    runtime_deps = [":plugin-lib"],
)

java_library(
    name = "plugin-lib",
    exports = PLUGIN_API + EXPORTS,
)

java_library(
    name = "plugin-lib-neverlink",
    neverlink = 1,
    exports = PLUGIN_API + EXPORTS,
)

java_binary(
    name = "plugin-api-sources",
    main_class = "Dummy",
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
)

# This is a generic test target for TypeScript plugins.
#
# `nodejs_test` needs to run in the directory where the `package.json` and
# `node_modules` are, so unfortunately we cannot move this target into the
# BUILD files of individual plugins. On the other hand one common target
# for all plugins also has the advantage of being re-usable.
#
# For making this work for a specific plugin you have make the source files
# of the plugin available as a `filegroup` and add it to the `data` attribute.
# And you have to specify the `PLUGIN_DIR` in the `env` attribute.
nodejs_test(
    name = "web-test-runner",
    size = "large",
    chdir = package_name(),
    data = [
        ":package.json",
        ":web-test-runner.config.mjs",
        # This is an example of how you could reference your plugin sources:
        # "//plugins/codemirror-editor/web:codemirror-test-sources",
        "@plugins_npm//:node_modules",
    ],
    entry_point = "@plugins_npm//:node_modules/@web/test-runner/dist/bin.js",
    env = {"PLUGIN_DIR": "codemirror-editor"},
    tags = [
        "local",
        "manual",
    ],
)
