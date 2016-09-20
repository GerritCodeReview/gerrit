load('//tools/bzl:pkg_war.bzl', 'pkg_war')

genrule(
  name = 'gen_version',
  stamp = 1,
  cmd = "grep BUILD_GERRIT_LABEL < bazel-out/volatile-status.txt | awk '{print $$2;}' > $@",
  outs = ['version.txt'],
  visibility = ['//visibility:public'],
)

pkg_war(name = 'gerrit')
pkg_war(name = 'headless', ui = None)
