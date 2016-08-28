load('//tools/bzl:genrule2.bzl', 'genrule2')
load('//tools/bzl:build.bzl', 'pkg_war')

genrule2(
  name = 'version',
  srcs = ['VERSION'],
  cmd = "grep GERRIT_VERSION $< | cut -d \"'\" -f 2 >$@",
  out = 'version.txt',
  visibility = ['//visibility:public'],
)

pkg_war(
  name = 'headless',
  context = [
    '//gerrit-main:main_bin_deploy.jar',
    '//gerrit-war:webapp_assets',
  ],
  libs = [
    '//gerrit-war:init',
    '//gerrit-war:log4j-config',
    '//gerrit-war:version',
    '//lib:postgresql',
    '//lib/log:impl_log4j',
   ],
  pgmlibs = [
    '//gerrit-pgm:pgm'
  ],
)
