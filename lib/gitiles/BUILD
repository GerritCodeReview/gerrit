load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_alias")
load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "gitiles",
    visibility = ["//visibility:public"],
    exports = [
        ":cm-autolink",
        ":commonmark",
        ":gfm-strikethrough",
        ":gfm-tables",
        ":gitiles-servlet",
        ":prettify",
        "//lib/commons:lang3",
        "//lib/commons:text",
    ],
)

java_library(
    name = "cm-autolink",
    data = ["//lib:LICENSE-commonmark"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_commonmark_commonmark_ext_autolink"],
)

java_library(
    name = "commonmark",
    data = ["//lib:LICENSE-commonmark"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_commonmark_commonmark"],
)

java_library(
    name = "gfm-strikethrough",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_commonmark_commonmark_ext_gfm_strikethrough"],
)

java_library(
    name = "gfm-tables",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_commonmark_commonmark_ext_gfm_tables"],
)

# Single flavour seam for the gitiles-servlet maven jar. The EE8 default
# resolves the javax gitiles-servlet (1.6.0) from the shared @external_deps;
# the EE11 branch resolves the jakarta gitiles-servlet from the isolated
# @external_deps_ee11. The select() lives here once: both the java_library
# below (compile classpath) and the gitiles plugin's static-resource genrule
# (raw jar) consume this single alias, so neither repeats the select.
# Stays flavoured_alias (NOT the toolchain resolver): this jar is consumed both
# as a JavaInfo dep AND as a raw file via $(location) by the gitiles plugin's
# static-resource genrule, and the resolver exposes only JavaInfo.
flavoured_alias(
    name = "gitiles-servlet-jar",
    default = "@external_deps//:com_google_gitiles_gitiles_servlet",
    flavours = {"ee11": "@external_deps_ee11//:com_google_gitiles_gitiles_servlet"},
    visibility = ["//visibility:public"],
)

java_library(
    name = "gitiles-servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [":gitiles-servlet-jar"],
)

java_library(
    name = "prettify",
    visibility = ["//visibility:public"],
    exports = ["@java-prettify"],
)
