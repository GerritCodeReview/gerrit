load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:pkg_war.bzl", "pkg_war")

# test

package(default_visibility = ["//visibility:public"])

config_setting(
    name = "java17",
    values = {
        "java_language_version": "17",
    },
)

genrule(
    name = "gen_version",
    outs = ["version.txt"],
    cmd = ("(cat bazel-out/volatile-status.txt bazel-out/stable-status.txt | " +
           "grep STABLE_BUILD_GERRIT_LABEL | cut -d ' ' -f 2) > $@ || echo 'UNKNOWN' > $@"),
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
    name = "release",
    context = ["//plugins:core"],
    doc = True,
)

pkg_war(
    name = "withdocs",
    doc = True,
)

API_DEPS = [
    "//java/com/google/gerrit/acceptance:framework_deploy.jar",
    "//java/com/google/gerrit/acceptance:libframework-lib-src.jar",
    "//java/com/google/gerrit/extensions:extension-api_deploy.jar",
    "//java/com/google/gerrit/extensions:libapi-src.jar",
    "//plugins:plugin-api_deploy.jar",
    "//plugins:plugin-api-sources_deploy.jar",
]

API_JAVADOC_DEPS = [
    "//java/com/google/gerrit/acceptance:framework-javadoc",
    "//java/com/google/gerrit/extensions:extension-api-javadoc",
    "//plugins:plugin-api-javadoc",
]

genrule2(
    name = "api",
    testonly = True,
    srcs = API_DEPS + API_JAVADOC_DEPS,
    outs = ["api.zip"],
    cmd = " && ".join([
        "cp $(SRCS) $$TMP",
        "cd $$TMP",
        "zip -qr $$ROOT/$@ .",
    ]),
)

genrule2(
    name = "api-skip-javadoc",
    testonly = True,
    srcs = API_DEPS,
    outs = ["api-skip-javadoc.zip"],
    cmd = " && ".join([
        "cp $(SRCS) $$TMP",
        "cd $$TMP",
        "zip -qr $$ROOT/$@ .",
    ]),
)
