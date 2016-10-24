load('//tools/bzl:pkg_war.bzl', 'pkg_war')

genrule(
  name = 'gen_version',
  stamp = 1,
  cmd = "grep STABLE_BUILD_GERRIT_LABEL < bazel-out/volatile-status.txt | cut -d ' ' -f 2 > $@",
  outs = ['version.txt'],
  visibility = ['//visibility:public'],
)

genrule(
  name = "LICENSES",
  srcs = ["//Documentation:licenses.txt"],
  cmd = "cp $< $@",
  outs = ["LICENSES.txt"],
  visibility = ['//visibility:public'],
)

pkg_war(name = 'gerrit')
pkg_war(name = 'headless', ui = None)
pkg_war(name = 'release', ui = 'ui_optdbg_r', context = ['//plugins:core'])
