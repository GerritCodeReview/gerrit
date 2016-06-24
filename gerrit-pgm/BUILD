load('//tools/bzl:java.bzl', 'java_library2')
load('//tools/bzl:junit.bzl', 'junit_tests')

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

java_library(
  name = 'init',
  srcs = glob([SRCS + 'init/*.java']),
  resources = glob([RSRCS + 'init/*']),
  deps = DEPS + [
    ':init-api',
    ':util',
    '//gerrit-common:annotations',
    '//gerrit-launcher:launcher', # We want this dep to be provided_deps
    '//gerrit-lucene:lucene',
    '//lib:args4j',
    '//lib:derby',
    '//lib:gwtjsonrpc',
    '//lib:gwtorm',
    '//lib:h2',
    '//lib/commons:validator',
    '//lib/mina:sshd',
  ],
  visibility = ['//visibility:public'],
)

REST_UTIL_DEPS = [
  '//gerrit-cache-h2:cache-h2',
  '//gerrit-util-cli:cli',
  '//lib:args4j',
  '//lib:gwtorm',
  '//lib/commons:dbcp',
]

java_library(
  name = 'util',
  exports = [':util-nodep'],
  runtime_deps = DEPS + REST_UTIL_DEPS,
  visibility = ['//visibility:public'],
)

java_library(
  name = 'util-nodep',
  srcs = glob([SRCS + 'util/*.java']),
  deps = DEPS + REST_UTIL_DEPS, #  We want all these deps to be provided_deps
  visibility = ['//visibility:public'],
)

JETTY_DEPS = [
  '//lib/jetty:jmx',
  '//lib/jetty:server',
  '//lib/jetty:servlet',
]

java_library(
  name = 'http',
  runtime_deps = DEPS + JETTY_DEPS,
  exports = [':http-jetty'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'http-jetty',
  srcs = glob([SRCS + 'http/jetty/*.java']),
  deps = JETTY_DEPS + BASE_JETTY_DEPS + [ # We want all these deps to be provided_deps
    '//gerrit-launcher:launcher',
    '//gerrit-reviewdb:client',
    '//lib:servlet-api-3_1',
  ],
  visibility = ['//visibility:public'],
)

REST_PGM_DEPS = [
  ':http',
  ':init',
  ':init-api',
  ':util',
  '//gerrit-cache-h2:cache-h2',
  '//gerrit-gpg:gpg',
  '//gerrit-lucene:lucene',
  '//gerrit-oauth:oauth',
  '//gerrit-openid:openid',
  '//lib:args4j',
  '//lib:gwtorm',
  '//lib:protobuf',
  '//lib:servlet-api-3_1-without-neverlink',
  '//lib/auto:auto-value',
  '//lib/prolog:cafeteria',
  '//lib/prolog:compiler',
  '//lib/prolog:runtime',
]

java_library(
  name = 'pgm',
  resources = glob([RSRCS + '*']),
  runtime_deps = DEPS + REST_PGM_DEPS + [
    ':daemon',
  ],
  visibility = ['//visibility:public'],
)

# no transitive deps, used for gerrit-acceptance-framework
java_library(
  name = 'daemon',
  srcs = glob([SRCS + '*.java', SRCS + 'rules/*.java']),
  resources = glob([RSRCS + '*']),
  deps = DEPS + REST_PGM_DEPS + [ # We want all these deps to be provided_deps
    '//gerrit-launcher:launcher',
  ],
  visibility = ['//visibility:public'],
)

junit_tests(
  name = 'pgm_tests',
  srcs = glob(['src/test/java/**/*.java']),
  deps = [
    ':init',
    ':init-api',
    ':pgm',
    '//gerrit-common:server',
    '//gerrit-server:server',
    '//lib:guava',
    '//lib:junit',
    '//lib/easymock:easymock',
    '//lib/guice:guice',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib/jgit/org.eclipse.jgit.junit:junit',
  ],
)
