include_defs('//tools/build.defs')

gerrit_war(name = 'gerrit')
gerrit_war(name = 'chrome',   ui = 'ui_chrome')
gerrit_war(name = 'firefox',  ui = 'ui_firefox')
gerrit_war(name = 'withdocs', docs = True)
gerrit_war(name = 'release',  docs = True, context = ['//plugins:core.zip'])

API_DEPS = [
  ':extension-api',
  ':extension-api-src',
  ':plugin-api',
  ':plugin-api-src',
]

genrule(
  name = 'api',
  cmd = ';'.join(
    ['cd $TMP'] +
    ['ln -s $(location %s) .' % n for n in API_DEPS] +
    ['zip -q0 $OUT *']),
  deps = API_DEPS,
  out = 'api.zip',
)

java_binary(
  name = 'extension-api',
  deps = [':extension-lib'],
  visibility = ['//tools/maven:'],
)

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
  cmd = 'ln -s $(location //gerrit-extension-api:api-src) $OUT',
  deps = ['//gerrit-extension-api:api-src'],
  out = 'extension-api-src.jar',
  visibility = ['//tools/maven:'],
)

PLUGIN_API = [
  '//gerrit-server:server',
  '//gerrit-pgm:init-api',
  '//gerrit-sshd:sshd',
  '//gerrit-httpd:httpd',
]

java_binary(
  name = 'plugin-api',
  deps = [':plugin-lib'],
  visibility = ['//tools/maven:'],
)

java_library(
  name = 'plugin-lib',
  deps = PLUGIN_API + ['//lib:servlet-api-3_0'],
  export_deps = True,
  visibility = ['PUBLIC'],
)

java_binary(
  name = 'plugin-api-src',
  deps = [
    '//gerrit-extension-api:api-src',
  ] + [d + '-src' for d in PLUGIN_API],
  visibility = ['//tools/maven:'],
)
