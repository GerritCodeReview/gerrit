load("@aspect_bazel_lib//lib:copy_to_bin.bzl", "copy_to_bin")
load("@aspect_rules_js//js:defs.bzl", "js_library")
load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_file")
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("@plugins_npm//:defs.bzl", "npm_link_all_packages")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//tools/bzl:javadoc.bzl", "java_doc")
load(
    "//tools/bzl:plugins.bzl",
    "CORE_PLUGINS",
    "CUSTOM_PLUGINS",
)

npm_link_all_packages(name = "node_modules")

package(default_visibility = ["//visibility:public"])

js_library(
    name = "plugins-config-lib",
    srcs = [
        ".prettierrc.js",
        "eslint-plugin.config.js",
        "rollup.config.js",
        "tsconfig-plugins-base.json",
    ],
)

# Core plugins that ship a legacy EE8 (javax.servlet) flavour target named
# "<n>-ee8". Under the EE11 default the core bundle uses the unsuffixed
# jakarta jars; the release-ee8.war transition swaps in the -ee8 twins, whose
# javax servlet sources cannot compile against the jakarta classpath. The
# other core plugins are servlet-free (Gerrit-Flavour: any) and bundle the
# same jar in both flavours, so only the servlet-coupled ones are listed.
EE8_FLAVOURED_PLUGINS = [
    "gitiles",
    "plugin-manager",
]

genrule2(
    name = "core",
    srcs = select({
        "//tools:ee11": [
            "//plugins/%s:%s" % (n, n) if n in EE8_FLAVOURED_PLUGINS else "//plugins/%s.jar" % (n if ":" in n else "%s:%s" % (n, n))
            for n in CORE_PLUGINS + CUSTOM_PLUGINS
        ],
        "//conditions:default": [
            "//plugins/%s:%s-ee8" % (n, n) if n in EE8_FLAVOURED_PLUGINS else "//plugins/%s.jar" % (n if ":" in n else "%s:%s" % (n, n))
            for n in CORE_PLUGINS + CUSTOM_PLUGINS
        ],
    }),
    outs = ["core.zip"],
    # The EE8 jars keep their "<n>-ee8.jar" filename so the WAR visibly
    # carries the flavour (gitiles.jar in the default release.war vs
    # gitiles-ee8.jar in the legacy release-ee8.war). The runtime plugin name
    # is unchanged: the loader takes it
    # from the Gerrit-PluginName manifest entry (firstNonNull, see
    # JarPluginProvider/PluginLoader), falling back to the filename only when
    # that is absent -- and every flavoured core plugin sets Gerrit-PluginName.
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
    "//java/com/google/gerrit/entities/converter:converters",
    "//java/com/google/gerrit/entities/converter:proto_converter",
    "//java/com/google/gerrit/server/api",
    "//java/com/google/gerrit/server/audit",
    "//java/com/google/gerrit/server/cache/mem",
    "//java/com/google/gerrit/server/cache/serialize",
    "//java/com/google/gerrit/server/data",
    "//java/com/google/gerrit/server/git/receive",
    "//java/com/google/gerrit/server/logging",
    "//java/com/google/gerrit/server/rules/prolog",
    "//java/com/google/gerrit/server/schema",
    "//java/com/google/gerrit/server/util/time",
    "//java/com/google/gerrit/proto",
    "//java/com/google/gerrit/util/cli",
    "//java/com/google/gerrit/util/logging",
    "//lib/antlr:java-runtime",
    "//lib/auto:auto-value-annotations",
    "//lib/auto:auto-value-gson",
    "//lib/commons:codec",
    "//lib/commons:compress",
    "//lib/commons:dbcp",
    "//lib/commons:lang3",
    "//lib/commons:io",
    "//lib/commons:net",
    "//lib/dropwizard:dropwizard-core",
    "//lib/flogger:api",
    "//lib/guice:guice",
    "//lib/guice:guice-assistedinject",
    "//lib/guice:guice-servlet",
    "//lib/httpcomponents:httpclient",
    "//lib/httpcomponents:httpcore",
    "//lib:jgit-servlet",
    "//lib:jgit",
    "//lib:jsr305",
    "//lib:jspecify",
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
    "//lib:soy",
    "//prolog:gerrit-prolog-common",
]

FLAVOUR_EXPORTS = [
    "//java/com/google/gerrit/util/http",
    "//lib:servlet-api-without-neverlink",
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
    exports = PLUGIN_API + EXPORTS + FLAVOUR_EXPORTS,
)

java_library(
    name = "plugin-lib-neverlink",
    neverlink = 1,
    exports = PLUGIN_API + EXPORTS + FLAVOUR_EXPORTS,
)

java_binary(
    name = "plugin-api-sources",
    main_class = "Dummy",
    # The httpd and util/http source jars are flavour-specific since the EE8/EE11
    # split (httpd is now an alias, so libhttpd-src.jar no longer exists). Select
    # the matching source jars so the EE8 sources artifact ships javax sources and,
    # under the flavour=ee11 transition, the EE11 sources artifact ships jakarta.
    runtime_deps = [
        "//antlr3:libquery_parser-src.jar",
        "//java/com/google/gerrit/common:libannotations-src.jar",
        "//java/com/google/gerrit/common:libserver-src.jar",
        "//java/com/google/gerrit/entities:libentities-src.jar",
        "//java/com/google/gerrit/extensions:libapi-src.jar",
        "//java/com/google/gerrit/index:libindex-src.jar",
        "//java/com/google/gerrit/index:libquery_exception-src.jar",
        "//java/com/google/gerrit/pgm/init/api:libapi-src.jar",
        "//java/com/google/gerrit/server:libserver-src.jar",
        "//java/com/google/gerrit/server/restapi:librestapi-src.jar",
        "//java/com/google/gerrit/sshd:libsshd-src.jar",
    ] + select({
        "//tools:ee11": [
            "//java/com/google/gerrit/httpd:libhttpd-ee11-src.jar",
            "//java/com/google/gerrit/util/http:libhttp-ee11-src.jar",
        ],
        "//conditions:default": [
            "//java/com/google/gerrit/httpd:libhttpd-ee8-src.jar",
            "//java/com/google/gerrit/util/http:libhttp-ee8-src.jar",
        ],
    }),
)

java_doc(
    name = "plugin-api-javadoc",
    libs = PLUGIN_API + [
        "//antlr3:query_parser",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/util/http",
    ],
    pkgs = ["com.google.gerrit"],
    title = "Gerrit Review Plugin API Documentation",
)

# EE8 (javax.servlet) legacy flavour of the plugin API deploy artifacts,
# published as gerrit-plugin-api-ee8 during the deprecation window so a legacy
# EE8 plugin can still be built standalone against the javax plugin API.
flavoured_file(
    name = "plugin-api-ee8",
    actual = ":plugin-api_deploy.jar",
    flavour = "ee8",
)

flavoured_file(
    name = "plugin-api-ee8-sources",
    actual = ":plugin-api-sources_deploy.jar",
    flavour = "ee8",
)

flavoured_file(
    name = "plugin-api-ee8-javadoc",
    actual = ":plugin-api-javadoc",
    flavour = "ee8",
)

copy_to_bin(
    name = "rollup.config",
    srcs = ["rollup.config.js"],
    visibility = ["//plugins:__subpackages__"],
)

copy_to_bin(
    name = "tsconfig-plugins-base",
    srcs = ["tsconfig-plugins-base.json"],
    visibility = ["//plugins:__subpackages__"],
)
