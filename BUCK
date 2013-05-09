include_defs('//tools/build.defs')

gerrit_war(name = 'gerrit')
gerrit_war(name = 'chrome',   ui = 'draft_safari')
gerrit_war(name = 'firefox',  ui = 'draft_gecko1_8')
gerrit_war(name = 'withdocs', context = DOCS)
gerrit_war(name = 'release',  context = DOCS + ['//plugins:core.zip'])

genrule(
  name = 'api',
  cmd = 'echo',
  srcs = [],
  deps = [
    ':extension-api',
    ':plugin-api',
  ],
  out = '__api__',
)

java_binary(name = 'extension-api', deps = [':extension-lib'])
java_library(
  name = 'extension-lib',
  deps = [
    '//gerrit-extension-api:api',
    '//lib/guice:guice',
    '//lib/guice:guice-servlet',
    '//lib:servlet-api-3_0',
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

genrule(
  name = 'download',
  cmd = 'buck build $(buck audit classpath --dot :eclipse_classpath' +
    '| egrep \'^  "//lib/\''+
    '| cut -d\\" -f2' +
    '| sort | uniq)',
  srcs = [],
  deps = [],
  out = '__fake.download__',
)

genrule(
  name = 'eclipse',
  cmd = '',
  srcs = [],
  deps = [
    ':_eclipse_project',
    ':_eclipse_classpath',
  ],
  out = 'eclipse',
)

genrule(
  name = 'eclipse_project',
  cmd = '',
  srcs = [],
  deps = [
    ':_eclipse_project',
    ':_eclipse_classpath_nocompile',
  ],
  out = 'eclipse',
)

java_library(
  name = 'eclipse_classpath',
  deps = LIBS + PGMLIBS + [
    '//gerrit-acceptance-tests:acceptance_tests',
    '//gerrit-gwtdebug:gwtdebug',
    '//gerrit-gwtui:ui_module',
    '//gerrit-httpd:httpd_tests',
    '//gerrit-main:main_lib',
    '//gerrit-server:server__compile',
    '//lib/prolog:compiler_lib',
  ] + scan_plugins(),
)

genrule(
  name = '_eclipse_project',
  cmd = '${//tools:eclipse_project} $OUT',
  srcs = [],
  deps = ['//tools:eclipse_project'],
  out = 'eclipse_project',
)

genrule(
  name = '_eclipse_classpath',
  cmd = '${//tools:eclipse_classpath} $OUT $DEPS',
  srcs = [],
  deps = [
    ':eclipse_classpath',
    '//tools:eclipse_classpath',
  ],
  out = 'eclipse_classpath',
)

genrule(
  name = '_eclipse_classpath_nocompile',
  cmd = '${//tools:eclipse_classpath}',
  srcs = [],
  deps = ['//tools:eclipse_classpath'],
  out = '_eclipse_classpath_nocompile',
)
