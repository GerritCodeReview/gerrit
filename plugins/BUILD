load("@aspect_bazel_lib//lib:copy_to_bin.bzl", "copy_to_bin")
load("@aspect_rules_js//js:defs.bzl", "js_library")
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "ee10_flavour_file")
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

# Core plugins that ship an EE10 (jakarta.servlet) flavour target named
# "<n>-ee10". Under --@com_googlesource_gerrit_bazlets//flags:flavour=ee10 the core bundle uses that jar instead
# of the default javax one, whose own servlet sources cannot compile against the
# jakarta classpath. The other core plugins are servlet-free and build unchanged
# under the flag, so only the servlet-coupled ones are listed here.
EE10_FLAVOURED_PLUGINS = [
    "gitiles",
    "plugin-manager",
]

genrule2(
    name = "core",
    srcs = select({
        "//tools:ee10": [
            "//plugins/%s:%s-ee10" % (n, n) if n in EE10_FLAVOURED_PLUGINS else "//plugins/%s.jar" % (n if ":" in n else "%s:%s" % (n, n))
            for n in CORE_PLUGINS + CUSTOM_PLUGINS
        ],
        "//conditions:default": [
            "//plugins/%s.jar" % (n if ":" in n else "%s:%s" % (n, n))
            for n in CORE_PLUGINS + CUSTOM_PLUGINS
        ],
    }),
    outs = ["core.zip"],
    # The EE10 jars keep their "<n>-ee10.jar" filename so the WAR visibly carries
    # the flavour (gitiles.jar in release.war vs gitiles-ee10.jar in
    # release-ee10.war). The runtime plugin name is unchanged: the loader takes it
    # from the Gerrit-PluginName manifest entry (firstNonNull, see
    # JarPluginProvider/PluginLoader), falling back to the filename only when that
    # is absent -- and every EE10-flavoured core plugin sets Gerrit-PluginName.
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

# The only servlet-flavour-bearing plugin-api exports that are not already
# flavour aliases. Under --@com_googlesource_gerrit_bazlets//flags:flavour=ee10 the plugin API exposes jakarta
# servlet-api and the jakarta util/http, matching the httpd / guice / jgit
# aliases (which flip on their own). This is what makes //plugins:plugin-api a
# plugin-api-ee10 under the flag, so EE10 plugins compile against jakarta.
_FLAVOUR_EXPORTS = select({
    "//tools:ee10": [
        "//java/com/google/gerrit/util/http:http-ee10",
        "//lib:jakarta-servlet-api-without-neverlink",
    ],
    "//conditions:default": [
        "//java/com/google/gerrit/util/http",
        "//lib:servlet-api-without-neverlink",
    ],
})

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
    exports = PLUGIN_API + EXPORTS + _FLAVOUR_EXPORTS,
)

java_library(
    name = "plugin-lib-neverlink",
    neverlink = 1,
    exports = PLUGIN_API + EXPORTS + _FLAVOUR_EXPORTS,
)

java_binary(
    name = "plugin-api-sources",
    main_class = "Dummy",
    # The httpd and util/http source jars are flavour-specific since the EE8/EE10
    # split (httpd is now an alias, so libhttpd-src.jar no longer exists). Select
    # the matching source jars so the EE8 sources artifact ships javax sources and,
    # under the flavour=ee10 transition, the EE10 sources artifact ships jakarta.
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
        "//tools:ee10": [
            "//java/com/google/gerrit/httpd:libhttpd-ee10-src.jar",
            "//java/com/google/gerrit/util/http:libhttp-ee10-src.jar",
        ],
        "//conditions:default": [
            "//java/com/google/gerrit/httpd:libhttpd-ee8-src.jar",
            "//java/com/google/gerrit/util/http:libhttp-src.jar",
        ],
    }),
)

java_doc(
    name = "plugin-api-javadoc",
    # util/http is flavour-specific (PLUGIN_API's httpd flips via its alias, but
    # util/http has no neutral alias), so select it like plugin-api-sources does.
    # Otherwise the EE10 javadoc (built under the flavour=ee10 transition) would
    # document the javax util/http instead of the jakarta http-ee10.
    libs = PLUGIN_API + [
        "//antlr3:query_parser",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/entities",
    ] + select({
        "//tools:ee10": ["//java/com/google/gerrit/util/http:http-ee10"],
        "//conditions:default": ["//java/com/google/gerrit/util/http"],
    }),
    pkgs = ["com.google.gerrit"],
    title = "Gerrit Review Plugin API Documentation",
)

# EE10 (jakarta.servlet) flavour of the plugin API deploy artifacts. Each wrapper
# builds its EE8 counterpart under the flavour=ee10 self-transition, so the jakarta
# jar/sources/javadoc are produced alongside the EE8 ones in a single invocation
# with no command-line flag. tools/maven publishes these to Maven Central as
# gerrit-plugin-api-ee10, so an EE10 plugin can be built standalone (outside the
# Gerrit tree) against the jakarta plugin API -- the EE10 analogue of the standalone
# build that consumes the default gerrit-plugin-api.
ee10_flavour_file(
    name = "plugin-api-ee10",
    actual = ":plugin-api_deploy.jar",
)

ee10_flavour_file(
    name = "plugin-api-ee10-sources",
    actual = ":plugin-api-sources_deploy.jar",
)

ee10_flavour_file(
    name = "plugin-api-ee10-javadoc",
    actual = ":plugin-api-javadoc",
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
