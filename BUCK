include_defs('//tools/build.defs')

gerrit_war(name = 'gerrit')
gerrit_war(name = 'chrome',   ui = 'ui_chrome')
gerrit_war(name = 'firefox',  ui = 'ui_firefox')
gerrit_war(name = 'withdocs', docs = True)
gerrit_war(name = 'release',  docs = True, context = ['//plugins:core.zip'])

API_DEPS = [
  ':extension-api',
  ':extension-api-src',
  ':extension-api-javadoc',
  ':plugin-api',
  ':plugin-api-src',
  ':plugin-api-javadoc',
  ':plugin-gwtui',
  ':plugin-gwtui-src',
  ':plugin-gwtui-javadoc',
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

genrule(
  name = 'extension-api',
  cmd = 'ln -s $(location //gerrit-extension-api:extension-api) $OUT',
  deps = ['//gerrit-extension-api:extension-api'],
  out = 'extension-api.jar',
  visibility = ['//tools/maven:'],
)

genrule(
  name = 'extension-api-src',
  cmd = 'ln -s $(location //gerrit-extension-api:api-src) $OUT',
  deps = ['//gerrit-extension-api:api-src'],
  out = 'extension-api-src.jar',
  visibility = ['//tools/maven:'],
)

genrule(
  name = 'extension-api-javadoc',
  cmd = 'ln -s $(location //gerrit-extension-api:api-javadoc) $OUT',
  deps = ['//gerrit-extension-api:api-javadoc'],
  out = 'extension-api-javadoc.jar',
  visibility = ['//tools/maven:'],
)

genrule(
  name = 'plugin-api',
  cmd = 'ln -s $(location //gerrit-plugin-api:api) $OUT',
  deps = ['//gerrit-plugin-api:api'],
  out = 'plugin-api.jar',
  visibility = ['//tools/maven:'],
)

genrule(
  name = 'plugin-api-src',
  cmd = 'ln -s $(location //gerrit-plugin-api:api-src) $OUT',
  deps = ['//gerrit-plugin-api:api-src'],
  out = 'plugin-api-src.jar',
  visibility = ['//tools/maven:'],
)

genrule(
  name = 'plugin-api-javadoc',
  cmd = 'ln -s $(location //gerrit-plugin-api:api-javadoc) $OUT',
  deps = ['//gerrit-plugin-api:api-javadoc'],
  out = 'plugin-api-javadoc.jar',
  visibility = ['//tools/maven:'],
)

genrule(
  name = 'plugin-gwtui',
  cmd = 'ln -s $(location //gerrit-plugin-gwtui:client) $OUT',
  deps = ['//gerrit-plugin-gwtui:client'],
  out = 'plugin-gwtui.jar',
  visibility = ['//tools/maven:'],
)

genrule(
  name = 'plugin-gwtui-src',
  cmd = 'ln -s $(location //gerrit-plugin-gwtui:src) $OUT',
  deps = ['//gerrit-plugin-gwtui:src'],
  out = 'plugin-gwtui-src.jar',
  visibility = ['//tools/maven:'],
)

genrule(
  name = 'plugin-gwtui-javadoc',
  cmd = 'ln -s $(location //gerrit-plugin-gwtui:api-javadoc) $OUT',
  deps = ['//gerrit-plugin-gwtui:api-javadoc'],
  out = 'plugin-gwtui-javadoc.jar',
  visibility = ['//tools/maven:'],
)
