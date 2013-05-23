include_defs('//tools/build.defs')

# TODO(davido): better place for it?
VERSION='2.8-SNAPSHOT'

gerrit_war(name = 'gerrit')
gerrit_war(name = 'chrome',   ui = 'ui_chrome')
gerrit_war(name = 'firefox',  ui = 'ui_firefox')
gerrit_war(name = 'withdocs', context = DOCS)
gerrit_war(name = 'release',  context = DOCS + ['//plugins:core.zip'])

install_api(name = 'install_api', version=VERSION, dep = [
    ':extension-api',
    ':plugin-api',
  ])

genrule(
  name = 'api',
  cmd = 'echo',
  srcs = [],
  deps = [
    ':extension-api',
    ':plugin-api',
  ],
  out = '__fake.api__',
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
  export_deps = True,
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
  export_deps = True,
  visibility = ['PUBLIC'],
)

genrule(
  name = 'download',
  cmd = '${//tools:download_all}',
  srcs = [],
  deps = ['//tools:download_all'],
  out = '__fake.download__',
)

genrule(
  name = 'download_sources',
  cmd = '${//tools:download_all} --src',
  srcs = [],
  deps = ['//tools:download_all'],
  out = '__fake.download__',
)
