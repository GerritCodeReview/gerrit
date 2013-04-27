include_defs('//lib/war.defs')

def gerrit_war(
    name,
    webapp_zips = [],
    ui = 'ui_optdbg'):
  war(
    name = name,
    libs = [
      '//gerrit-common:version',
      '//gerrit-war:init',
      '//gerrit-war:log4j-config',
      '//lib:postgresql',
      '//lib/log:impl_log4j',
    ],
    pgmlibs = [
      '//gerrit-pgm:pgm',
    ],
    webapp_jars = [
      '//gerrit-main:main_bin',
    ],
    webapp_zips = [
      '//gerrit-war:webapp_assets',
      '//gerrit-gwtexpui:clippy_swf',
      '//gerrit-gwtui:' + ui,
    ] + webapp_zips,
  )

gerrit_war(name = 'gerrit')
gerrit_war(
  name = 'release',
  webapp_zips = [
    '//Documentation:html',
    '//plugins:core',
  ],
)

genrule(
  name = 'api',
  cmd = 'echo',
  srcs = [],
  deps = [':extension-api', ':plugin-api'],
  out = '__api__',
)

java_binary(name = 'extension-api', deps = [':extension-lib'])
java_binary(
  name = 'extension-lib',
  deps = [
    '//gerrit-extension-api:api',
    '//lib/guice:guice',
    '//lib/guice:guice-servlet',
    '//lib:servlet-api-2_5',
  ],
  visibility = ['PUBLIC'],
)

java_binary(name = 'plugin-api', deps = [':plugin-lib'])
java_library(
  name = 'plugin-lib',
  deps = [
    '//gerrit-server:server',
    '//gerrit-sshd:sshd',
    '//gerrit-httpd:httpd',
  ],
  visibility = ['PUBLIC'],
)
