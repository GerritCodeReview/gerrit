include_defs('//tools/build.defs')

gerrit_war(name = 'gerrit')
gerrit_war(name = 'chrome',   ui = 'ui_chrome')
gerrit_war(name = 'firefox',  ui = 'ui_firefox')
gerrit_war(name = 'withdocs', context = DOCS)
gerrit_war(name = 'release',  context = DOCS + ['//plugins:core.zip'])

API_DEPS = [
  ':extension-api',
  ':extension-api-src',
  ':plugin-api',
  ':plugin-api-src',
]

genrule(
  name = 'api',
  cmd = '',
  srcs = [],
  deps = API_DEPS,
  out = '__fake.api__',
)

maven_install(deps = API_DEPS)
maven_deploy(deps = API_DEPS)

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
genrule(
  name = 'extension-api-src',
  cmd = 'ln -s $DEPS $OUT',
  srcs = [],
  deps = ['//gerrit-extension-api:api-src'],
  out = 'extension-api-src.jar',
)

PLUGIN_API = [
  '//gerrit-server:server',
  '//gerrit-pgm:init',
  '//gerrit-sshd:sshd',
  '//gerrit-httpd:httpd',
]

java_binary(name = 'plugin-api', deps = [':plugin-lib'])
java_library(
  name = 'plugin-lib',
  deps = PLUGIN_API,
  export_deps = True,
  visibility = ['PUBLIC'],
)
java_binary(
  name = 'plugin-api-src',
  deps = [
    '//gerrit-extension-api:api-src',
  ] + [d + '-src' for d in PLUGIN_API],
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
