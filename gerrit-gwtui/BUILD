load(
    "//tools/bzl:gwt.bzl",
    "gen_ui_module",
    "gwt_genrule",
    "gwt_user_agent_permutations",
)
load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:license.bzl", "license_test")
load("//tools/bzl:java.bzl", "java_library2")

gwt_genrule()

gwt_genrule(suffix = "_r")

gen_ui_module(name = "ui_module")

gen_ui_module(
    name = "ui_module",
    suffix = "_r",
)

gwt_user_agent_permutations()

java_library2(
    name = "client-lib",
    srcs = glob(["src/main/**/*.java"]),
    exported_deps = [":ui_module"],
    resources = glob(["src/main/**/*"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-gwtui-common:client-lib",
        "//lib/gwt:dev",
        "//lib/gwt:user",
    ],
)

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
