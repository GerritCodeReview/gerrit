load("@com_googlesource_gerrit_bazlets//tools:servlet_flavour.bzl", "servlet_flavour_deps")
load("@rules_java//java:defs.bzl", "java_library")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

exports_files(glob([
    "LICENSE-*",
]))

filegroup(
    name = "all-licenses",
    srcs = glob(
        ["LICENSE-*"],
        exclude = ["LICENSE-DO_NOT_DISTRIBUTE"],
    ),
    visibility = ["//visibility:public"],
)

# The concrete per-flavour servlet-api targets are bound only by the
# servlet-flavour toolchain (and the ee10 twins by the ee10 jetty adapter / test
# fakes). Flavour-neutral code depends on the neutral //lib:servlet-api instead,
# so the restricted visibility here is the guardrail that keeps a concrete javax
# jar from silently leaking into the EE10 WAR.
java_library(
    name = "servlet-api-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    neverlink = 1,
    visibility = ["//tools/flavour_toolchain:__pkg__"],
    exports = ["@external_deps//:javax_servlet_javax_servlet_api"],
)

java_library(
    name = "servlet-api-without-neverlink-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//tools/flavour_toolchain:__pkg__"],
    exports = ["@external_deps//:javax_servlet_javax_servlet_api"],
)

# jakarta.servlet API for the EE10 release flavour (neverlink: the Jetty EE10
# container provides it at runtime). Restricted to its direct users: the
# toolchain binding, the Jetty ee10 adapter, and the ee10 test fakes.
java_library(
    name = "servlet-api-ee10",
    data = ["//lib:LICENSE-Apache2.0"],
    neverlink = 1,
    visibility = [
        "//javatests/com/google/gerrit/util/http/testutil/ee10:__pkg__",
        "//lib/jetty:__pkg__",
        "//tools/flavour_toolchain:__pkg__",
    ],
    exports = ["@external_deps//:jakarta_servlet_jakarta_servlet_api"],
)

# Non-neverlink jakarta servlet-api (bundled at runtime). Bound only by the
# toolchain; runtime consumers use the neutral servlet-api-without-neverlink.
java_library(
    name = "servlet-api-without-neverlink-ee10",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//tools/flavour_toolchain:__pkg__"],
    exports = ["@external_deps//:jakarta_servlet_jakarta_servlet_api"],
)

# Flavour-neutral non-neverlink servlet API (bundled in the WAR): javax by
# default, jakarta under --@com_googlesource_gerrit_bazlets//flags:flavour=ee10. Used by code that packages the
# servlet API into the WAR (e.g. //java/com/google/gerrit/pgm).
servlet_flavour_deps(
    name = "servlet-api-without-neverlink",
    license_deps = [
        ":servlet-api-without-neverlink-ee10",
        ":servlet-api-without-neverlink-ee8",
    ],
    part = "servlet_api_runtime",
    visibility = ["//visibility:public"],
)

# Flavour-neutral servlet API, backed by the servlet-flavour TOOLCHAIN: resolves
# to the neverlink javax servlet-api (EE8 default) or jakarta servlet-api (EE10)
# from the active //flags:flavour, with no select() here. Boundary code depends
# on this target so one source set serves both flavours.
servlet_flavour_deps(
    name = "servlet-api",
    license_deps = [
        ":servlet-api-ee10",
        ":servlet-api-ee8",
    ],
    part = "servlet_api",
    visibility = ["//visibility:public"],
)

java_library(
    name = "gson",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_code_gson_gson"],
)

java_library(
    name = "jgit",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit:jgit"],
    runtime_deps = [
        ":javaewah",
        "//lib/log:api",
    ],
)

java_library(
    name = "jgit-ssh-apache",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.ssh.apache:ssh-apache"],
    runtime_deps = [
        "//lib/mina:sshd-sftp",
    ],
)

java_library(
    name = "jgit-archive",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.archive:jgit-archive"],
    runtime_deps = [
        ":jgit",
        "//lib/commons:compress",
    ],
)

java_library(
    name = "jgit-junit",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.junit:junit"],
    runtime_deps = [":jgit"],
)

# Flavour-aware JGit servlet module: dependents use //lib:jgit-servlet and get
# JGit's generated .ee8 (javax) bridge by default or the canonical jakarta module
# under --@com_googlesource_gerrit_bazlets//flags:flavour=ee10. Keeps javax.servlet out of the EE10 WAR.
servlet_flavour_deps(
    name = "jgit-servlet",
    license_deps = [
        ":jgit-servlet-ee10",
        ":jgit-servlet-ee8",
    ],
    part = "jgit_servlet",
    visibility = ["//visibility:public"],
)

java_library(
    name = "jgit-servlet-ee8",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.http.server.ee8:jgit-servlet-ee8"],
    runtime_deps = [":jgit"],
)

# Canonical jakarta.servlet JGit servlet module for the EE10 flavour. The .ee8
# bridge above is javax; this exports JGit master's canonical jakarta module.
# Same FQDNs, so the two must never share a classpath.
java_library(
    name = "jgit-servlet-ee10",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.http.server:jgit-servlet"],
    runtime_deps = [":jgit"],
)

java_library(
    name = "javaewah",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_googlecode_javaewah_JavaEWAH"],
)

java_library(
    name = "protobuf",
    data = ["//lib:LICENSE-protobuf"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_protobuf_protobuf_java"],
)

java_library(
    name = "guava-failureaccess",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_guava_failureaccess"],
)

java_library(
    name = "j2objc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_j2objc_j2objc_annotations"],
)

java_library(
    name = "guava",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guava-failureaccess",
        ":j2objc",
        "@external_deps//:com_google_guava_guava",
    ],
)

java_library(
    name = "guava-testlib",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@external_deps//:com_google_guava_guava_testlib",
    ],
)

java_library(
    name = "caffeine",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = [
        "//java/com/google/gerrit/acceptance:__pkg__",
        "//java/com/google/gerrit/server/cache/mem:__pkg__",
    ],
    exports = ["@external_deps//:com_github_ben_manes_caffeine_caffeine"],
)

java_library(
    name = "caffeine-guava",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = [
        "//java/com/google/gerrit/acceptance:__pkg__",
        "//java/com/google/gerrit/server/cache/mem:__pkg__",
    ],
    exports = ["@external_deps//:com_github_ben_manes_caffeine_guava"],
)

java_library(
    name = "args4j",
    data = ["//lib:LICENSE-args4j"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:args4j_args4j"],
)

java_library(
    name = "automaton",
    data = ["//lib:LICENSE-automaton"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:dk_brics_automaton"],
)

java_library(
    name = "flexmark-all-lib",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_vladsch_flexmark_flexmark_all_lib"],
)

java_library(
    name = "autolink",
    data = ["//lib:LICENSE-autolink"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_nibor_autolink_autolink"],
)

java_library(
    name = "tukaani-xz",
    data = ["//lib:LICENSE-xz"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_tukaani_xz"],
)

java_library(
    name = "mime-util",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:eu_medsea_mimeutil_mime_util"],
)

java_library(
    name = "guava-retrying",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_github_rholder_guava_retrying"],
    runtime_deps = [":jsr305"],
)

java_library(
    name = "jsr305",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_code_findbugs_jsr305"],
)

java_library(
    name = "blame-cache",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_gitiles_blame_cache"],
)

java_library(
    name = "h2",
    data = ["//lib:LICENSE-h2"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_h2database_h2"],
)

java_library(
    name = "jimfs",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_jimfs_jimfs"],
    runtime_deps = [":guava"],
)

java_library(
    name = "junit",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":hamcrest",
        "@external_deps//:junit_junit",
    ],
    runtime_deps = [":hamcrest"],
)

java_library(
    name = "hamcrest",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_hamcrest_hamcrest"],
)

java_library(
    name = "soy",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_template_soy"],
    runtime_deps = [
        ":args4j",
        ":gson",
        ":guava",
        ":html-types",
        ":icu4j",
        ":jsr305",
        ":protobuf",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/ow2:ow2-asm",
        "//lib/ow2:ow2-asm-analysis",
        "//lib/ow2:ow2-asm-commons",
        "//lib/ow2:ow2-asm-util",
    ],
)

java_library(
    name = "html-types",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_google_common_html_types_types"],
)

java_library(
    name = "icu4j",
    data = ["//lib:LICENSE-icu4j"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_ibm_icu_icu4j"],
)

java_library(
    name = "roaringbitmap",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@external_deps//:org_roaringbitmap_RoaringBitmap",
        "@external_deps//:org_roaringbitmap_shims",
    ],
)

sh_test(
    name = "nongoogle_test",
    srcs = ["nongoogle_test.sh"],
    data = ["//tools:nongoogle.toml"],
)
