load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_war")
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("@npm//:defs.bzl", "npm_link_all_packages")
load("//tools/bzl:pkg_war.bzl", "pkg_war")

npm_link_all_packages(name = "node_modules")

package(default_visibility = ["//visibility:public"])

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

# Servlet release flavours. `bazelisk build //:release` / //:headless build
# the EE11 (jakarta.servlet) default. The -ee8 targets build the same WARs
# with flavour=ee8 forced via a configuration transition, emitting distinctly
# named release-ee8.war / headless-ee8.war -- the transitional legacy
# artifacts, deprecated for one release train; both flavours build side by
# side in one invocation. No -ee11 name exists: the jakarta flavour was never
# published under a suffixed name, so the unsuffixed targets simply became it.
flavoured_war(
    name = "release-ee8",
    flavour = "ee8",
    war = ":release",
)

flavoured_war(
    name = "gerrit-ee8",
    flavour = "ee8",
    war = ":gerrit",
)

flavoured_war(
    name = "headless-ee8",
    flavour = "ee8",
    war = ":headless",
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
