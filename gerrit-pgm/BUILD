SRCS = 'src/main/java/com/google/gerrit/pgm/'
RSRCS = 'src/main/resources/com/google/gerrit/pgm/'

INIT_API_SRCS = glob([SRCS + 'init/api/*.java'])

BASE_JETTY_DEPS = [
  '//gerrit-common:server',
  '//gerrit-extension-api:api',
  '//gerrit-gwtexpui:linker_server',
  '//gerrit-gwtexpui:server',
  '//gerrit-httpd:httpd',
  '//gerrit-server:server',
  '//gerrit-sshd:sshd',
  '//lib:guava',
  '//lib/guice:guice',
  '//lib/guice:guice-assistedinject',
  '//lib/guice:guice-servlet',
  '//lib/jgit/org.eclipse.jgit:jgit',
  '//lib/log:api',
  '//lib/log:log4j',
]

DEPS = BASE_JETTY_DEPS + [
  '//gerrit-reviewdb:server',
  '//lib/log:jsonevent-layout',
]

java_library(
  name = 'init-api',
  srcs = INIT_API_SRCS,
  deps = DEPS + ['//gerrit-common:annotations'],
  visibility = ['//visibility:public'],
)
