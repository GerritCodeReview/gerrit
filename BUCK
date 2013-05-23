include_defs('//VERSION')
include_defs('//tools/build.defs')

gerrit_war(name = 'gerrit')
gerrit_war(name = 'chrome',   ui = 'ui_chrome')
gerrit_war(name = 'firefox',  ui = 'ui_firefox')
gerrit_war(name = 'withdocs', context = DOCS)
gerrit_war(name = 'release',  context = DOCS + ['//plugins:core.zip'])

API_DEPS = [
    ':extension-api-%s' % GERRIT_VER,
    ':plugin-api-%s' % GERRIT_VER,
  ]

api_util(
  name = 'install',
  deps = API_DEPS
)

api_util(
  name = 'deploy',
  deps = API_DEPS
)

genrule(
  name = 'api',
  cmd = 'echo',
  srcs = [],
  deps = API_DEPS,
  out = '__fake.api__',
)

java_binary(
  name = 'extension-api-%s' % GERRIT_VER,
  deps = [':extension-lib'],
)

java_library(
  name = 'extension-lib',
  deps = [
    '//gerrit-extension-api:api',
    '//gerrit-extension-api:api_src',
    '//lib/guice:guice',
    '//lib/guice:guice-servlet',
    '//lib:servlet-api-3_0',
  ],
  export_deps = True,
  visibility = ['PUBLIC'],
)

java_binary(
  name = 'plugin-api-%s' % GERRIT_VER,
  deps = [
    '//gerrit-plugin-api:api_src',
    ':plugin-lib',
  ],
)

java_library(
  name = 'plugin-lib',
  deps = [
    '//gerrit-server:server',
    '//gerrit-server:server_src',
    '//gerrit-sshd:sshd',
    '//gerrit-sshd:sshd_src',
    '//gerrit-httpd:httpd',
    '//gerrit-httpd:httpd_src',
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
