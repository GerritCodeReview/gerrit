load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "ee10_flavour_file")
load("@rules_shell//shell:sh_test.bzl", "sh_test")
load("//tools/bzl:asciidoc.bzl", "documentation_attributes", "genasciidoc", "genasciidoc_zip")
load("//tools/bzl:license.bzl", "license_map")
load("//tools/bzl:war_checks.bzl", "war_jars_allowlist_test")

package(default_visibility = ["//visibility:public"])

exports_files([
    "replace_macros.py",
])

filegroup(
    name = "prettify_files",
    srcs = [
        ":prettify.min.css",
        ":prettify.min.js",
    ],
)

genrule(
    name = "prettify_min_css",
    srcs = ["//resources/com/google/gerrit/prettify:client/prettify.css"],
    outs = ["prettify.min.css"],
    cmd = "cp $< $@",
)

genrule(
    name = "prettify_min_js",
    srcs = ["//resources/com/google/gerrit/prettify:client/prettify.js"],
    outs = ["prettify.min.js"],
    cmd = "cp $< $@",
)

filegroup(
    name = "resources",
    srcs = glob(
        [
            "images/*.jpg",
            "images/*.png",
        ],
        allow_empty = True,
    ) + [
        ":prettify_files",
        "//:LICENSES.txt",
    ],
)

# Flavour-aware allowlist (B): an alias holds the select() so the test macro
# still receives a single label. A plain run resolves the EE8 allowlist; under
# --@com_googlesource_gerrit_bazlets//flags:flavour=ee10 the WAR *and* the
# allowlist both flip to EE10, so check_release_war_jars stays coherent under the
# global flag instead of failing against a hard-coded EE8 list.
alias(
    name = "release_war_jars_allowlist",
    actual = select({
        "//tools:ee10": ":release_ee10_war_jars.txt",
        "//conditions:default": ":release_war_jars.txt",
    }),
)

war_jars_allowlist_test(
    name = "check_release_war_jars",
    allowlist = ":release_war_jars_allowlist",
    war_jars_manifest = "//:release.war.jars.txt",
)

# EE10 release WAR jar-set guard (A). Self-transitioning: `ee10_flavour_file`
# rebuilds the jar manifest under the flavour=ee10 transition, so a plain
# `bazel test //Documentation:check_release_ee10_war_jars` (no flag) validates the
# jakarta WAR's third-party jar set alongside the EE8 guard above. Together with
# the flavour-aware allowlist this means `bazel test //...` covers BOTH flavours
# and no one needs the global flag to test the EE10 WAR.
ee10_flavour_file(
    name = "release-ee10.war.jars.txt",
    actual = "//:release.war.jars.txt",
)

war_jars_allowlist_test(
    name = "check_release_ee10_war_jars",
    allowlist = ":release_ee10_war_jars.txt",
    war_jars_manifest = ":release-ee10.war.jars.txt",
)

license_map(
    name = "licenses",
    json_maps = [
        "//polygerrit-ui/app/node_modules_licenses:polygerrit-licenses.json",
    ],
    opts = ["--asciidoctor"],
    targets = [
        "//polygerrit-ui/app:polygerrit_ui",
        "//java/com/google/gerrit/pgm",
    ],
)

license_map(
    name = "backend_licenses",
    opts = ["--asciidoctor"],
    targets = [
        "//java/com/google/gerrit/pgm",
    ],
)

license_map(
    name = "js_licenses",
    json_maps = [
        "//polygerrit-ui/app/node_modules_licenses:polygerrit-licenses.json",
    ],
    targets = [
        "//polygerrit-ui/app:polygerrit_ui",
    ],
)

sh_test(
    name = "check_licenses",
    srcs = ["check_licenses_test.sh"],
    data = [
        "backend_licenses.gen.txt",
        "backend_licenses.txt",
        "js_licenses.gen.txt",
        "js_licenses.txt",
        "licenses.gen.txt",
        "licenses.txt",
    ],
)

DOC_DIR = "Documentation"

SRCS = glob(["*.txt"])

genrule(
    name = "index",
    srcs = SRCS,
    outs = ["index.jar"],
    cmd = "$(location //java/com/google/gerrit/asciidoctor:doc_indexer) " +
          "-o $(OUTS) " +
          "--prefix \"%s/\" " % DOC_DIR +
          "--in-ext \".txt\" " +
          "--out-ext \".html\" " +
          "$(SRCS)",
    tools = ["//java/com/google/gerrit/asciidoctor:doc_indexer"],
)

# For the same srcs, we can have multiple genasciidoc_zip rules, but only one
# genasciidoc rule. Because multiple genasciidoc rules will have conflicting
# output files.
genasciidoc(
    name = "Documentation",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
)

genasciidoc_zip(
    name = "html",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
    directory = DOC_DIR,
)

genasciidoc_zip(
    name = "searchfree",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
    directory = DOC_DIR,
    searchbox = False,
)

genasciidoc_zip(
    name = "searchfree_safe",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
    directory = DOC_DIR,
    searchbox = False,
    webfonts = False,
)
