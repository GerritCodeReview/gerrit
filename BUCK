include_defs('//lib/war.defs')

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

# TODO(sop): Remove hack after Buck supports Eclipse
genrule(
  name = 'eclipse',
  cmd = 'lib/eclipse.py',
  srcs = [],
  deps = [
    ':gerrit',
    '//gerrit-httpd:httpd_tests',
  ],
  out = '__generate_eclipse__',
)
