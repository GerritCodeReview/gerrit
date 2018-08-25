package(default_visibility = ["//visibility:public"])

load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:pkg_war.bzl", "pkg_war")
load(
    "@bazel_tools//tools/jdk:default_java_toolchain.bzl",
    "default_java_toolchain",
)

config_setting(
    name = "java9",
    values = {
        "java_toolchain": "@bazel_tools//tools/jdk:toolchain_java9",
    },
)

config_setting(
    name = "java10",
    values = {
        "java_toolchain": "@bazel_tools//tools/jdk:toolchain_java10",
    },
)

config_setting(
    name = "java11",
    values = {
        "java_toolchain": ":toolchain_vanilla",
    },
)

java_runtime(
    name = "jdk11",
    java_home = "/usr/lib64/jvm/java-11",
    visibility = ["//visibility:public"],
)

filegroup(
    name = "vanillajavabuilder",
    srcs = ["@bazel_tools//tools/jdk:VanillaJavaBuilder_deploy.jar"],
)

default_java_toolchain(
    name = "toolchain_vanilla",
    forcibly_disable_header_compilation = True,
    javabuilder = [":vanillajavabuilder"],
    jvm_opts = [],
)

genrule(
    name = "gen_version",
    outs = ["version.txt"],
    cmd = ("cat bazel-out/volatile-status.txt bazel-out/stable-status.txt | " +
           "grep STABLE_BUILD_GERRIT_LABEL | cut -d ' ' -f 2 > $@"),
    stamp = 1,
    visibility = ["//visibility:public"],
)

genrule(
    name = "LICENSES",
    srcs = ["//Documentation:licenses.txt"],
    outs = ["LICENSES.txt"],
    cmd = "cp $< $@",
    visibility = ["//visibility:public"],
)

pkg_war(
    name = "gerrit",
    ui = "polygerrit",
)

pkg_war(
    name = "headless",
    ui = None,
)

pkg_war(
    name = "polygerrit",
    ui = "polygerrit",
)

pkg_war(
    name = "release",
    context = ["//plugins:core"],
    doc = True,
    ui = "ui_optdbg_r",
)

pkg_war(
    name = "withdocs",
    doc = True,
)

API_DEPS = [
    "//java/com/google/gerrit/acceptance:framework_deploy.jar",
    "//java/com/google/gerrit/acceptance:libframework-lib-src.jar",
    "//java/com/google/gerrit/acceptance:framework-javadoc",
    "//java/com/google/gerrit/extensions:extension-api_deploy.jar",
    "//java/com/google/gerrit/extensions:libapi-src.jar",
    "//java/com/google/gerrit/extensions:extension-api-javadoc",
    "//plugins:plugin-api_deploy.jar",
    "//plugins:plugin-api-sources_deploy.jar",
    "//plugins:plugin-api-javadoc",
    "//gerrit-plugin-gwtui:gwtui-api_deploy.jar",
    "//gerrit-plugin-gwtui:gwtui-api-source_deploy.jar",
    "//gerrit-plugin-gwtui:gwtui-api-javadoc",
]

genrule2(
    name = "api",
    testonly = 1,
    srcs = API_DEPS,
    outs = ["api.zip"],
    cmd = " && ".join([
        "cp $(SRCS) $$TMP",
        "cd $$TMP",
        "zip -qr $$ROOT/$@ .",
    ]),
)
