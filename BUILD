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

# EE10 (jakarta.servlet) release flavour — PROTOTYPE / placeholder.
#
# `bazelisk build //:release` stays the EE8 default and is untouched. This
# target is the seam for the future `release-ee10.war`; for now it is an
# essentially empty WAR carrying only a `Gerrit-Flavour: ee10` marker so the
# target exists and builds green. Content is added incrementally as each
# flavour tier lands (jakarta servlet-api, Jetty EE10, generated httpd-ee10,
# plugin-api-ee10, core-ee10 plugins) — see the ordered task list and status map
# in the gerrit-ee8-ee10-flavoured-release-design repo
# (TODO-ee10-gerrit-flavour.md, README.md).
genrule(
    name = "release-ee10",
    outs = ["release-ee10.war"],
    cmd = " && ".join([
        "set -e",
        "d=$$(mktemp -d)",
        "mkdir -p $$d/META-INF",
        "{ echo 'Manifest-Version: 1.0'; " +
        "echo 'Gerrit-Flavour: ee10'; " +
        "echo 'Implementation-Title: Gerrit Code Review (EE10 flavour, prototype placeholder)'; " +
        "} > $$d/META-INF/MANIFEST.MF",
        "(cd $$d && zip -qrX out.war META-INF)",
        "cp $$d/out.war $@",
    ]),
)

# Live handshake for the flavour seam: `bazelisk build //:flavour-check` writes
# flavour=ee8; `bazelisk build --define=flavour=ee10 //:flavour-check` writes
# flavour=ee10. Proves `--define=flavour=ee10` -> //tools:ee10 -> select()
# end-to-end without any new dep or synthetic source.
genrule(
    name = "flavour-check",
    outs = ["flavour-check.txt"],
    cmd = select({
        "//tools:ee10": "echo 'flavour=ee10' > $@",
        "//conditions:default": "echo 'flavour=ee8' > $@",
    }),
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
