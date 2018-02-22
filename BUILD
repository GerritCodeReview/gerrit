package(default_visibility = ["//visibility:public"])

load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:pkg_war.bzl", "pkg_war")

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

sh_binary(
    name = "start_test",
    srcs = ["start_test.sh"],
    data = [
        ":contrib/populate-fixture-data.py",
        ":polygerrit.war",
        "@local_jdk//:java",
    ],
)

load("@io_bazel_rules_webtesting//web:java.bzl", "java_web_test_suite")

java_web_test_suite(
    name = "PilotTest",
    srcs = ["PilotTest.java"],
    browsers = [
        # For experimental purposes only. Eventually you should
        # create your own browser definitions.
        "@io_bazel_rules_webtesting//browsers:chromium-local",
    ],
    local = True,
    deps = [
        "@io_bazel_rules_webtesting//testing/web",
        "@io_bazel_rules_webtesting//java/com/google/testing/web",
        "@junit",
        "@org_seleniumhq_selenium_api",
    ],
)
