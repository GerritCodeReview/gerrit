load(
    "//tools/bzl:gwt.bzl",
    "gwt_genrule",
    "gen_ui_module",
    "gwt_user_agent_permutations",
)
load("//tools/bzl:license.bzl", "license_test")
load("//tools/bzl:junit.bzl", "junit_tests")

gwt_genrule()

gwt_genrule("_r")

gen_ui_module(name = "ui_module")

gen_ui_module(
    name = "ui_module",
    suffix = "_r",
)

gwt_user_agent_permutations()

license_test(
    name = "ui_module_license_test",
    target = ":ui_module",
)

junit_tests(
    name = "ui_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":ui_module",
        "//gerrit-common:client",
        "//gerrit-extension-api:client",
        "//lib:junit",
        "//lib:truth",
        "//lib/gwt:dev",
        "//lib/gwt:user",
    ],
)
