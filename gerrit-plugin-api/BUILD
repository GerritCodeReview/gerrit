SRCS = [
  'gerrit-server/src/main/java/',
  'gerrit-httpd/src/main/java/',
  'gerrit-sshd/src/main/java/',
]

PLUGIN_API = [
  '//gerrit-httpd:httpd',
  '//gerrit-pgm:init-api',
  '//gerrit-server:server',
  '//gerrit-sshd:sshd',
]

java_binary(
  name = 'plugin-api',
  main_class = 'Dummy',
  runtime_deps = [':lib'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'lib',
  exports = PLUGIN_API + [
    '//gerrit-antlr:query_exception',
    '//gerrit-antlr:query_parser',
    '//gerrit-common:annotations',
    '//gerrit-common:server',
    '//gerrit-extension-api:api',
    '//gerrit-gwtexpui:server',
    '//gerrit-reviewdb:server',
    '//lib:args4j',
    '//lib:blame-cache',
    '//lib/dropwizard:dropwizard-core',
    '//lib:guava',
    '//lib:gwtorm',
    '//lib:jsch',
    '//lib:mime-util',
    '//lib:servlet-api-3_1',
    '//lib:velocity',
    '//lib/commons:lang',
    '//lib/guice:guice',
    '//lib/guice:guice-assistedinject',
    '//lib/guice:guice-servlet',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib/jgit/org.eclipse.jgit.http.server:jgit-servlet',
    '//lib/joda:joda-time',
    '//lib/log:api',
    '//lib/mina:sshd',
  ],
  visibility = ['//visibility:public'],
)
