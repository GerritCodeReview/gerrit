load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:pkg_war.bzl", "pkg_war")

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

pkg_war(name = "gerrit")

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
    "//gerrit-acceptance-framework:acceptance-framework_deploy.jar",
    "//gerrit-acceptance-framework:liblib-src.jar",
    "//gerrit-acceptance-framework:acceptance-framework-javadoc",
    "//gerrit-extension-api:extension-api_deploy.jar",
    "//gerrit-extension-api:libapi-src.jar",
    "//gerrit-extension-api:extension-api-javadoc",
    "//gerrit-plugin-api:plugin-api_deploy.jar",
    "//gerrit-plugin-api:plugin-api-sources_deploy.jar",
    "//gerrit-plugin-api:plugin-api-javadoc",
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
