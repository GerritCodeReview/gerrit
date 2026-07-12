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

# The jakarta gitiles-servlet maven jar (2.x, the only flavour since the EE8
# retirement). Kept as an alias: the jar is consumed both as a JavaInfo dep
# AND as a raw file via $(location) by the gitiles plugin's static-resource
# genrule.
alias(
    name = "gitiles-servlet-jar",
    actual = "@external_deps//:com_google_gitiles_gitiles_servlet",
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
