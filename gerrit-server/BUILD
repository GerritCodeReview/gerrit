load('//tools/bzl:junit.bzl', 'junit_tests')

CONSTANTS_SRC = [
  'src/main/java/com/google/gerrit/server/documentation/Constants.java',
]

SRCS = glob(
  ['src/main/java/**/*.java'],
  exclude = CONSTANTS_SRC,
)
RESOURCES =  glob(['src/main/resources/**/*'])

java_library(
  name = 'constants',
  srcs = CONSTANTS_SRC,
  visibility = ['//visibility:public'],
)

java_library(
  name = 'server',
  srcs = SRCS,
  resources = RESOURCES,
  deps = [
    ':constants',
    '//gerrit-antlr:query_exception',
    '//gerrit-antlr:query_parser',
    '//gerrit-common:annotations',
    '//gerrit-common:server',
    '//gerrit-extension-api:api',
    '//gerrit-patch-commonsnet:commons-net',
    '//gerrit-patch-jgit:server',
    '//gerrit-prettify:server',
    '//gerrit-reviewdb:server',
    '//gerrit-util-cli:cli',
    '//gerrit-util-ssl:ssl',
    '//lib:args4j',
    '//lib:automaton',
    '//lib:blame-cache',
    '//lib:grappa',
    '//lib:gson',
    '//lib:guava',
    '//lib:guava-retrying',
    '//lib:gwtjsonrpc',
    '//lib:gwtorm',
    '//lib:jsch',
    '//lib:juniversalchardet',
    '//lib:mime-util',
    '//lib:pegdown',
    '//lib:protobuf',
    '//lib:servlet-api-3_1',
    '//lib:soy',
    '//lib:tukaani-xz',
    '//lib:velocity',
    '//lib/antlr:java_runtime',
    '//lib/auto:auto-value',
    '//lib/commons:codec',
    '//lib/commons:compress',
    '//lib/commons:dbcp',
    '//lib/commons:lang',
    '//lib/commons:net',
    '//lib/commons:validator',
    '//lib/dropwizard:dropwizard-core',
    '//lib/guice:guice',
    '//lib/guice:guice-assistedinject',
    '//lib/guice:guice-servlet',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib/jgit/org.eclipse.jgit.archive:jgit-archive',
    '//lib/joda:joda-time',
    '//lib/log:api',
    '//lib/log:jsonevent-layout',
    '//lib/log:log4j',
    '//lib/lucene:lucene-analyzers-common',
    '//lib/lucene:lucene-core-and-backward-codecs',
    '//lib/lucene:lucene-queryparser',
    '//lib/mime4j:core',
    '//lib/mime4j:dom',
    '//lib/ow2:ow2-asm',
    '//lib/ow2:ow2-asm-tree',
    '//lib/ow2:ow2-asm-util',
    '//lib/prolog:runtime',
  ],
  visibility = ['//visibility:public'],
)

TESTUTIL_DEPS = [
  ':server',
  '//gerrit-common:server',
  '//gerrit-cache-h2:cache-h2',
  '//gerrit-extension-api:api',
  '//gerrit-gpg:gpg',
  '//gerrit-lucene:lucene',
  '//gerrit-reviewdb:server',
  '//lib:gwtorm',
  '//lib:h2',
  '//lib:truth',
  '//lib/guice:guice',
  '//lib/guice:guice-servlet',
  '//lib/jgit/org.eclipse.jgit:jgit',
  '//lib/jgit/org.eclipse.jgit.junit:junit',
  '//lib/joda:joda-time',
  '//lib/log:api',
  '//lib/log:impl_log4j',
  '//lib/log:log4j',
]

TESTUTIL = glob([
  'src/test/java/com/google/gerrit/testutil/**/*.java',
  'src/test/java/com/google/gerrit/server/project/Util.java',
])

java_library(
  name = 'testutil',
  srcs = TESTUTIL,
  deps = TESTUTIL_DEPS + [
    '//lib/auto:auto-value',
    '//lib/easymock:easymock',
    '//lib/powermock:powermock-api-easymock',
    '//lib/powermock:powermock-api-support',
    '//lib/powermock:powermock-core',
    '//lib/powermock:powermock-module-junit4',
    '//lib/powermock:powermock-module-junit4-common',
  ],
  exports = [
    '//lib/easymock:easymock',
    '//lib/powermock:powermock-api-easymock',
    '//lib/powermock:powermock-api-support',
    '//lib/powermock:powermock-core',
    '//lib/powermock:powermock-module-junit4',
    '//lib/powermock:powermock-module-junit4-common',
  ],
  visibility = ['//visibility:public'],
)

PROLOG_TEST_CASE = [
  'src/test/java/com/google/gerrit/rules/PrologTestCase.java',
]
PROLOG_TESTS = glob(
  ['src/test/java/com/google/gerrit/rules/**/*.java'],
  exclude = PROLOG_TEST_CASE,
)

java_library(
  name = 'prolog_test_case',
  srcs = PROLOG_TEST_CASE,
  deps = [
    ':server',
    ':testutil',
    '//gerrit-common:server',
    '//gerrit-extension-api:api',
    '//lib:guava',
    '//lib:junit',
    '//lib:truth',
    '//lib/guice:guice',
    '//lib/prolog:runtime',
  ],
)

junit_tests(
  name = 'prolog_tests',
  srcs = PROLOG_TESTS,
  resources = glob(['src/test/resources/com/google/gerrit/rules/**/*']),
  deps = TESTUTIL_DEPS + [
    ':prolog_test_case',
    ':testutil',
    '//gerrit-server/src/main/prolog:common',
    '//lib/prolog:runtime',
  ],
)

QUERY_TESTS = glob(
  ['src/test/java/com/google/gerrit/server/query/**/*.java'],
)

java_library(
  name = 'query_tests_code',
  srcs = QUERY_TESTS,
  deps = TESTUTIL_DEPS + [
    ':testutil',
    '//gerrit-antlr:query_exception',
    '//gerrit-antlr:query_parser',
    '//gerrit-common:annotations',
    '//gerrit-server/src/main/prolog:common',
    '//lib/antlr:java_runtime',
  ],
  visibility = ['//visibility:public'],
)

junit_tests(
  name = 'query_tests',
  srcs = QUERY_TESTS,
  deps = TESTUTIL_DEPS + [
    ':testutil',
    '//gerrit-antlr:query_exception',
    '//gerrit-antlr:query_parser',
    '//gerrit-common:annotations',
    '//gerrit-server/src/main/prolog:common',
    '//lib/antlr:java_runtime',
  ],
  visibility = ['//visibility:public'],
)

junit_tests(
  name = 'server_tests',
  srcs = glob(
    ['src/test/java/**/*.java'],
    exclude = TESTUTIL + PROLOG_TESTS + PROLOG_TEST_CASE + QUERY_TESTS
  ),
  resources = glob(['src/test/resources/com/google/gerrit/server/mail/*']),
  deps = TESTUTIL_DEPS + [
    ':testutil',
    '//gerrit-antlr:query_exception',
    '//gerrit-common:annotations',
    '//gerrit-patch-jgit:server',
    '//gerrit-server/src/main/prolog:common',
    '//lib:args4j',
    '//lib:grappa',
    '//lib:gson',
    '//lib:guava',
    '//lib:guava-retrying',
    '//lib:protobuf',
    '//lib/dropwizard:dropwizard-core',
    '//lib/guice:guice-assistedinject',
    '//lib/prolog:runtime',
  ],
  visibility = ['//visibility:public'],
)

load('//tools/bzl:javadoc.bzl', 'java_doc')

java_doc(
  name = 'doc',
  title = 'Gerrit Review Server Documentation',
  libs = [':server'],
  pkgs = ['com.google.gerrit'],
)
