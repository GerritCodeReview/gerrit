package(default_visibility = ["//visibility:public"])

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
