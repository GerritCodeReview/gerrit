include_defs('//tools/build.defs')

gerrit_war(name = 'gerrit')
gerrit_war(name = 'headless', ui = None)
gerrit_war(name = 'chrome',   ui = 'ui_chrome')
gerrit_war(name = 'firefox',  ui = 'ui_firefox')
gerrit_war(name = 'safari',   ui = 'ui_safari')
gerrit_war(name = 'withdocs', docs = True)
gerrit_war(name = 'release',  ui = 'ui_optdbg_r', docs = True, context = ['//plugins:core'],  visibility = ['//tools/maven:'])

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
  cmd = 'echo done >$OUT',
  deps = API_DEPS,
  out = '__fake.api__',
)

genrule(
  name = 'all',
  cmd = 'echo done >$OUT',
  deps = [
    ':api',
    ':release',
  ],
  out = '__fake.all__',
)

genrule(
  name = 'daemon_cmd',
  cmd = 'echo "java -jar $(location :gerrit) \$*" > $OUT; chmod +x $OUT',
  out = 'daemon.sh',
)

sh_binary(
  name = 'daemon',
  main = ':daemon_cmd',
)
