load("@rules_java//java:defs.bzl", "java_import", "java_library")

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

java_import(
    name = "gitiles-servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    jars = ["@gitiles_servlet//file"],
    visibility = ["//visibility:public"],
    deps = [
        ":cm-autolink",
        ":commonmark",
        ":gfm-strikethrough",
        ":gfm-tables",
        ":prettify",
        "//lib:servlet-api",
    ],
)

java_library(
    name = "prettify",
    visibility = ["//visibility:public"],
    exports = ["@java-prettify"],
)

java_import(
    name = "intraline-diff",
    data = ["//lib:LICENSE-Apache2.0"],
    jars = ["@gitiles_intraline_diff//file"],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib:jgit",
    ],
)
