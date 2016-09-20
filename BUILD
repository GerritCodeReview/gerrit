load('//tools/bzl:genrule2.bzl', 'genrule2')
load('//tools/bzl:pkg_war.bzl', 'pkg_war')

genrule2(
  name = 'version',
  srcs = ['VERSION'],
  cmd = "grep GERRIT_VERSION $< | cut -d \"'\" -f 2 >$@",
  out = 'version.txt',
  visibility = ['//visibility:public'],
)

pkg_war(name = 'gerrit')
pkg_war(name = 'headless', ui = [])

