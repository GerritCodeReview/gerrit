include_defs('//tools/build.defs')

gerrit_war(name = 'gerrit')
gerrit_war(name = 'chrome',   ui = 'ui_chrome')
gerrit_war(name = 'firefox',  ui = 'ui_firefox')
gerrit_war(name = 'withdocs', docs = True)
gerrit_war(name = 'release',  docs = True, context = ['//plugins:core'],  visibility = ['//tools/maven:'])

API_DEPS = [
  '//gerrit-extension-api:extension-api',
  '//gerrit-extension-api:extension-api-src',
  '//gerrit-extension-api:extension-api-javadoc',
  '//gerrit-plugin-api:plugin-api',
  '//gerrit-plugin-api:plugin-api-src',
  '//gerrit-plugin-api:plugin-api-javadoc',
  '//gerrit-plugin-gwtui:gwtui-api',
  '//gerrit-plugin-gwtui:gwtui-api-src',
  '//gerrit-plugin-gwtui:gwtui-api-javadoc',
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
