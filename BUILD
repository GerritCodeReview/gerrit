load("@aspect_rules_js//js:defs.bzl", "js_library")
load("@buildifier_prebuilt//:rules.bzl", "buildifier")
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("@npm//:defs.bzl", "npm_link_all_packages")
load("//tools/bzl:pkg_war.bzl", "pkg_war")

package(default_visibility = ["//visibility:public"])

npm_link_all_packages(name = "node_modules")

js_library(
    name = "eslintrc",
    srcs = ["eslint.config.js"],
    visibility = ["//visibility:public"],
    deps = [
        "//polygerrit-ui/app:eslint_config_lib",
    ],
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

buildifier(
    name = "buildifier",
    lint_mode = "fix",
    mode = "fix",
    tags = [
        "manual",
    ],
)
