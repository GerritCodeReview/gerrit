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

java_library(
    name = "servlet-api-3_1",
    data = ["//lib:LICENSE-Apache2.0"],
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@servlet_api_3_1//jar"],
)

java_library(
    name = "servlet-api-3_1-without-neverlink",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@servlet_api_3_1//jar"],
)

java_library(
    name = "gwtjsonrpc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gwtjsonrpc//jar"],
)

java_library(
    name = "gwtjsonrpc_src",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gwtjsonrpc//jar:src"],
)

java_library(
    name = "gson",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gson//jar"],
)

java_library(
    name = "gwtorm_client",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gwtorm_client//jar"],
)

java_library(
    name = "gwtorm_client_src",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gwtorm_client//jar:src"],
)

java_library(
    name = "protobuf",
    data = ["//lib:LICENSE-protobuf"],
    visibility = ["//visibility:public"],
    exports = ["@protobuf//jar"],
)

java_library(
    name = "gwtorm",
    visibility = ["//visibility:public"],
    exports = [":gwtorm_client"],
    runtime_deps = [":protobuf"],
)

java_library(
    name = "j2objc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@j2objc//jar"],
)

java_library(
    name = "guava",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@guava//jar"],
    runtime_deps = [":j2objc"],
)

java_library(
    name = "jsch",
    data = ["//lib:LICENSE-jsch"],
    visibility = ["//visibility:public"],
    exports = ["@jsch//jar"],
)

java_library(
    name = "juniversalchardet",
    data = ["//lib:LICENSE-MPL1.1"],
    visibility = ["//visibility:public"],
    exports = ["@juniversalchardet//jar"],
)

java_library(
    name = "args4j",
    data = ["//lib:LICENSE-args4j"],
    visibility = ["//visibility:public"],
    exports = ["@args4j//jar"],
)

java_library(
    name = "automaton",
    data = ["//lib:LICENSE-automaton"],
    visibility = ["//visibility:public"],
    exports = ["@automaton//jar"],
)

java_library(
    name = "flexmark",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark//jar"],
    runtime_deps = [
        ":flexmark-ext-abbreviation",
    ],
)

java_library(
    name = "flexmark-ext-abbreviation",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-abbreviation//jar"],
    runtime_deps = [
        ":flexmark-ext-anchorlink",
    ],
)

java_library(
    name = "flexmark-ext-anchorlink",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-anchorlink//jar"],
    runtime_deps = [
        ":flexmark-ext-autolink",
    ],
)

java_library(
    name = "flexmark-ext-autolink",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-autolink//jar"],
    runtime_deps = [
        ":flexmark-ext-definition",
    ],
)

java_library(
    name = "flexmark-ext-definition",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-definition//jar"],
    runtime_deps = [
        ":flexmark-ext-emoji",
    ],
)

java_library(
    name = "flexmark-ext-emoji",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-emoji//jar"],
    runtime_deps = [
        ":flexmark-ext-escaped-character",
    ],
)

java_library(
    name = "flexmark-ext-escaped-character",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-escaped-character//jar"],
    runtime_deps = [
        ":flexmark-ext-footnotes",
    ],
)

java_library(
    name = "flexmark-ext-footnotes",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-footnotes//jar"],
    runtime_deps = [
        ":flexmark-ext-gfm-issues",
    ],
)

java_library(
    name = "flexmark-ext-gfm-issues",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-gfm-issues//jar"],
    runtime_deps = [
        ":flexmark-ext-gfm-strikethrough",
    ],
)

java_library(
    name = "flexmark-ext-gfm-strikethrough",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-gfm-strikethrough//jar"],
    runtime_deps = [
        ":flexmark-ext-gfm-tables",
    ],
)

java_library(
    name = "flexmark-ext-gfm-tables",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-gfm-tables//jar"],
    runtime_deps = [
        ":flexmark-ext-gfm-tasklist",
    ],
)

java_library(
    name = "flexmark-ext-gfm-tasklist",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-gfm-tasklist//jar"],
    runtime_deps = [
        ":flexmark-ext-gfm-users",
    ],
)

java_library(
    name = "flexmark-ext-gfm-users",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-gfm-users//jar"],
    runtime_deps = [
        ":flexmark-ext-ins",
    ],
)

java_library(
    name = "flexmark-ext-ins",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-ins//jar"],
    runtime_deps = [
        ":flexmark-ext-jekyll-front-matter",
    ],
)

java_library(
    name = "flexmark-ext-jekyll-front-matter",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-jekyll-front-matter//jar"],
    runtime_deps = [
        ":flexmark-ext-superscript",
    ],
)

java_library(
    name = "flexmark-ext-superscript",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-superscript//jar"],
    runtime_deps = [
        ":flexmark-ext-tables",
    ],
)

java_library(
    name = "flexmark-ext-tables",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-tables//jar"],
    runtime_deps = [
        ":flexmark-ext-toc",
    ],
)

java_library(
    name = "flexmark-ext-toc",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-toc//jar"],
    runtime_deps = [
        ":flexmark-ext-typographic",
    ],
)

java_library(
    name = "flexmark-ext-typographic",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-typographic//jar"],
    runtime_deps = [
        ":flexmark-ext-wikilink",
    ],
)

java_library(
    name = "flexmark-ext-wikilink",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-wikilink//jar"],
    runtime_deps = [
        ":flexmark-ext-yaml-front-matter",
    ],
)

java_library(
    name = "flexmark-ext-yaml-front-matter",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-ext-yaml-front-matter//jar"],
    runtime_deps = [
        ":flexmark-formatter",
    ],
)

java_library(
    name = "flexmark-formatter",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-formatter//jar"],
    runtime_deps = [
        ":flexmark-html-parser",
    ],
)

java_library(
    name = "flexmark-html-parser",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-html-parser//jar"],
    runtime_deps = [
        ":flexmark-profile-pegdown",
    ],
)

java_library(
    name = "flexmark-profile-pegdown",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-profile-pegdown//jar"],
    runtime_deps = [
        ":flexmark-util",
    ],
)

java_library(
    name = "flexmark-util",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@flexmark-util//jar"],
)

java_library(
    name = "autolink",
    data = ["//lib:LICENSE-autolink"],
    visibility = ["//visibility:public"],
    exports = ["@autolink//jar"],
)

java_library(
    name = "grappa",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@grappa//jar"],
    runtime_deps = [
        ":jitescript",
        "//lib/ow2:ow2-asm",
        "//lib/ow2:ow2-asm-analysis",
        "//lib/ow2:ow2-asm-tree",
        "//lib/ow2:ow2-asm-util",
    ],
)

java_library(
    name = "jitescript",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jitescript//jar"],
)

java_library(
    name = "tukaani-xz",
    data = ["//lib:LICENSE-xz"],
    visibility = ["//visibility:public"],
    exports = ["@tukaani_xz//jar"],
)

java_library(
    name = "mime-util",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@mime_util//jar"],
)

java_library(
    name = "guava-retrying",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@guava_retrying//jar"],
    runtime_deps = [":jsr305"],
)

java_library(
    name = "jsr305",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jsr305//jar"],
)

java_library(
    name = "blame-cache",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@blame_cache//jar"],
)

java_library(
    name = "h2",
    data = ["//lib:LICENSE-h2"],
    visibility = ["//visibility:public"],
    exports = ["@h2//jar"],
)

java_library(
    name = "jimfs",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@jimfs//jar"],
    runtime_deps = [":guava"],
)

java_library(
    name = "junit",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":hamcrest-core",
        "@junit//jar",
    ],
    runtime_deps = [":hamcrest-core"],
)

java_library(
    name = "hamcrest-core",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@hamcrest_core//jar"],
)

java_library(
    name = "truth",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":guava",
        ":junit",
        "@truth//jar",
    ],
)

java_library(
    name = "truth-java8-extension",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":guava",
        ":truth",
        "@truth-java8-extension//jar",
    ],
)

java_library(
    name = "javassist",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@javassist//jar"],
)

java_library(
    name = "derby",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@derby//jar"],
)

java_library(
    name = "soy",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@soy//jar"],
    runtime_deps = [
        ":args4j",
        ":gson",
        ":guava",
        ":html-types",
        ":icu4j",
        ":jsr305",
        ":protobuf",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:javax-inject",
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
    exports = ["@html_types//jar"],
)

java_library(
    name = "icu4j",
    data = ["//lib:LICENSE-icu4j"],
    visibility = ["//visibility:public"],
    exports = ["@icu4j//jar"],
)

java_library(
    name = "postgresql",
    data = ["//lib:LICENSE-postgresql"],
    visibility = ["//visibility:public"],
    exports = ["@postgresql//jar"],
)
