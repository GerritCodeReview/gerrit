load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("@rules_java//java:defs.bzl", "java_library")

# Canonical EE8/javax httpd sources. Shared by the EE8 :httpd library below and,
# via the to_jakarta transform, by the generated EE10 flavour (:httpd-ee10-srcs).
filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

# Flavour-aware httpd: dependents use //java/com/google/gerrit/httpd:httpd and
# get the EE8/javax library by default or the EE10/jakarta library under
# --define=flavour=ee10. Lets the release WAR (and everything else depending on
# httpd) flip flavour without per-dependent changes, the same way //lib/guice
# does. The two impls share FQDNs, so only one is ever on a classpath.
alias(
    name = "httpd",
    actual = select({
        "//tools:ee10": ":httpd-ee10",
        "//conditions:default": ":httpd-ee8",
    }),
    visibility = ["//visibility:public"],
)

java_library(
    name = "httpd-ee8",
    srcs = [":srcs"],
    resource_strip_prefix = "resources",
    resources = [
        "//resources/com/google/gerrit/httpd",
        "//resources/com/google/gerrit/httpd/raw",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/json",
        "//java/com/google/gerrit/launcher",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/audit",
        "//java/com/google/gerrit/server/cancellation",
        "//java/com/google/gerrit/server/git/receive",
        "//java/com/google/gerrit/server/ioutil",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/util/cli",
        "//java/com/google/gerrit/util/http",
        "//lib:args4j",
        "//lib:gson",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-servlet",
        "//lib:servlet-api",
        "//lib:soy",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/commons:lang3",
        "//lib/commons:net",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
        "//lib/jsoup",
    ],
)

# --- EE10 (jakarta.servlet) forward-generated httpd sources -------------------
# The httpd package is Gerrit's servlet boundary. Its EE10 flavour is generated
# from the canonical javax sources by rewriting only servlet/Jetty imports
# (javax.servlet -> jakarta.servlet, jetty ee8 -> ee10), preserving Java package
# names and source line numbers (same FQDNs as :httpd, so the two must never
# share a classpath). The genuinely divergent Jetty-core bootstrap files
# (HiddenErrorHandler, JettyServer in //java/com/google/gerrit/pgm/http/jetty)
# are NOT mechanical and get hand-written EE10 overlays separately; httpd itself
# is purely mechanical. Compiling :httpd-ee10-srcs into a java_library needs the
# Guice-7 dependency cascade and is a follow-up. See the EE10 design / TODO.
javax_to_jakarta_srcjar(
    name = "httpd-ee10-srcs",
    direction = "to_jakarta",
    sources = [":srcs"],
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

# EE10 (jakarta.servlet) httpd library, compiled from the transform-generated
# sources. Build only under --define=flavour=ee10: the Guice tier then resolves
# to Guice 7 (the cascade), and the servlet-neutral core deps recompile against
# it, so the whole graph is jakarta + Guice 7. Deps mirror :httpd with the
# servlet tier swapped to jakarta (jakarta-servlet-api, jgit-servlet-jakarta);
# //lib/guice:* keep their labels and auto-flip via select().
java_library(
    name = "httpd-ee10",
    srcs = [":httpd-ee10-srcs"],
    resource_strip_prefix = "resources",
    resources = [
        "//resources/com/google/gerrit/httpd",
        "//resources/com/google/gerrit/httpd/raw",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/json",
        "//java/com/google/gerrit/launcher",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/audit",
        "//java/com/google/gerrit/server/cancellation",
        "//java/com/google/gerrit/server/git/receive",
        "//java/com/google/gerrit/server/ioutil",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/util/cli",
        "//java/com/google/gerrit/util/http:http-ee10",
        "//lib:args4j",
        "//lib:gson",
        "//lib:guava",
        "//lib:jakarta-servlet-api",
        "//lib:jgit",
        "//lib:jgit-servlet-jakarta",
        "//lib:soy",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/commons:lang3",
        "//lib/commons:net",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
        "//lib/jsoup",
    ],
)
