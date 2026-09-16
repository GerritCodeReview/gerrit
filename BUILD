load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("@npm//:defs.bzl", "npm_link_all_packages")
load("//tools/bzl:pkg_war.bzl", "pkg_war")

npm_link_all_packages(name = "node_modules")

package(default_visibility = ["//visibility:public"])

_JGIT_STAMPED_WAR_LIBS = ["@jgit//org.eclipse.jgit:jgit-stamped"]

_JGIT_NON_STAMPED_WAR_EXCLUDES = ["libjgit.jar"]

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
    additional_libs = _JGIT_STAMPED_WAR_LIBS,
    exclude_jar_prefixes = _JGIT_NON_STAMPED_WAR_EXCLUDES,
    ui = "polygerrit",
)

pkg_war(
    name = "headless",
    additional_libs = _JGIT_STAMPED_WAR_LIBS,
    exclude_jar_prefixes = _JGIT_NON_STAMPED_WAR_EXCLUDES,
    ui = None,
)

pkg_war(
    name = "release",
    additional_libs = _JGIT_STAMPED_WAR_LIBS,
    context = ["//plugins:core"],
    doc = True,
    exclude_jar_prefixes = _JGIT_NON_STAMPED_WAR_EXCLUDES,
)

pkg_war(
    name = "withdocs",
    additional_libs = _JGIT_STAMPED_WAR_LIBS,
    doc = True,
    exclude_jar_prefixes = _JGIT_NON_STAMPED_WAR_EXCLUDES,
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
