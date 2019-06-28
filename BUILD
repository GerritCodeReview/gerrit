load(
    "@bazel_tools//tools/jdk:default_java_toolchain.bzl",
    "default_java_toolchain",
)
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:pkg_war.bzl", "pkg_war")

config_setting(
    name = "java9",
    values = {
        "java_toolchain": "@bazel_tools//tools/jdk:toolchain_java9",
    },
)

config_setting(
    name = "java10",
    values = {
        "java_toolchain": ":toolchain_vanilla",
    },
)

# TODO(davido): Switch to consuming it from @bazel_tool//tools/jdk:absolute_javabase
# when new Bazel version is released with this change included:
# https://github.com/bazelbuild/bazel/issues/6012
# https://github.com/bazelbuild/bazel/commit/0173bdbf7bdd1874379d4dd3eb70d5321e0f1816
# As the interim use a hack that works around it by putting the variable reference
# behind a select
config_setting(
    name = "use_absolute_javabase",
    values = {"define": "USE_ABSOLUTE_JAVABASE=true"},
)

java_runtime(
    name = "absolute_javabase",
    java_home = select({
        "//conditions:default": "",
        ":use_absolute_javabase": "$(ABSOLUTE_JAVABASE)",
    }),
    visibility = ["//visibility:public"],
)

# TODO(davido): Switch to consuming it from @bazel_tool//tools/jdk:toolchain_vanilla
# when my change is included in released Bazel version:
# https://github.com/bazelbuild/bazel/commit/0bef68e054eccecd690e5d9f46db8a0c4b2d887a
#default_java_toolchain(
#    name = "toolchain_vanilla",
#    forcibly_disable_header_compilation = True,
#    javabuilder = ["@bazel_tools//tools/jdk:VanillaJavaBuilder_deploy.jar"],
#    jvm_opts = [],
#)

package(default_visibility = ["//visibility:public"])

genrule(
    name = "gen_version",
    outs = ["version.txt"],
    cmd = ("cat bazel-out/volatile-status.txt bazel-out/stable-status.txt | " +
           "grep STABLE_BUILD_GERRIT_LABEL | cut -d ' ' -f 2 > $@"),
    stamp = 1,
)

genrule(
    name = "LICENSES",
    srcs = ["//Documentation:licenses.txt"],
    outs = ["LICENSES.txt"],
    cmd = "cp $< $@",
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
    testonly = True,
    srcs = API_DEPS,
    outs = ["api.zip"],
    cmd = " && ".join([
        "cp $(SRCS) $$TMP",
        "cd $$TMP",
        "zip -qr $$ROOT/$@ .",
    ]),
)
