load("//tools/bzl:gwt.bzl", "gwt_module")
load("//tools/bzl:junit.bzl", "junit_tests")

SRC = "src/main/java/com/google/gwtexpui/"

gwt_module(
    name = "Clippy",
    srcs = glob([SRC + "clippy/client/*.java"]),
    data = [
        "//lib:LICENSE-clippy",
        "//lib:LICENSE-silk_icons",
    ],
    gwt_xml = SRC + "clippy/Clippy.gwt.xml",
    resources = [
        SRC + "clippy/client/clippy.css",
        SRC + "clippy/client/clippy.swf",
        SRC + "clippy/client/page_white_copy.png",
        SRC + "clippy/client/CopyableLabelText.properties",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":SafeHtml",
        ":UserAgent",
        "//lib/gwt:user-neverlink",
    ],
)

java_library(
    name = "CSS",
    srcs = glob([SRC + "css/rebind/*.java"]),
    resources = [SRC + "css/CSS.gwt.xml"],
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:dev"],
)

gwt_module(
    name = "GlobalKey",
    srcs = glob([SRC + "globalkey/client/*.java"]),
    gwt_xml = SRC + "globalkey/GlobalKey.gwt.xml",
    resources = [
        SRC + "globalkey/client/KeyConstants.properties",
        SRC + "globalkey/client/key.css",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":SafeHtml",
        ":UserAgent",
        "//lib/gwt:user",
    ],
)

java_library(
    name = "linker_server",
    srcs = glob([SRC + "linker/server/*.java"]),
    visibility = ["//visibility:public"],
    deps = ["//lib:servlet-api-3_1"],
)

gwt_module(
    name = "Progress",
    srcs = glob([SRC + "progress/client/*.java"]),
    gwt_xml = SRC + "progress/Progress.gwt.xml",
    resources = [SRC + "progress/client/progress.css"],
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:user"],
)

gwt_module(
    name = "SafeHtml",
    srcs = glob([SRC + "safehtml/client/*.java"]),
    gwt_xml = SRC + "safehtml/SafeHtml.gwt.xml",
    resources = [SRC + "safehtml/client/safehtml.css"],
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:user"],
)

gwt_module(
    name = "UserAgent",
    srcs = glob([SRC + "user/client/*.java"]),
    gwt_xml = SRC + "user/User.gwt.xml",
    resources = [SRC + "user/client/tooltip.css"],
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:user"],
)

java_library(
    name = "server",
    srcs = glob([SRC + "server/*.java"]),
    visibility = ["//visibility:public"],
    deps = ["//lib:servlet-api-3_1"],
)

java_library(
    name = "client-src-lib",
    srcs = [],
    resources = glob(
        [SRC + n for n in [
            "clippy/**/*",
            "globalkey/**/*",
            "safehtml/**/*",
            "user/**/*",
        ]],
    ),
    visibility = ["//visibility:public"],
)
