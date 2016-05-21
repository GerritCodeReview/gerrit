load('//tools/bzl:java.bzl', 'java_library2')

SRCS = glob(['src/test/java/com/google/gerrit/acceptance/*.java'])

DEPS = [
  '//gerrit-gpg:gpg',
  '//gerrit-launcher:launcher',
  '//gerrit-openid:openid',
  '//gerrit-pgm:daemon',
  '//gerrit-pgm:http-jetty',
  '//gerrit-pgm:util-nodep',
  '//gerrit-server/src/main/prolog:common',
  '//gerrit-server:testutil',
  '//lib/auto:auto-value',
  '//lib/httpcomponents:fluent-hc',
  '//lib/httpcomponents:httpclient',
  '//lib/httpcomponents:httpcore',
  '//lib/jetty:servlet',
  '//lib/jgit/org.eclipse.jgit.junit:junit',
  '//lib/log:impl_log4j',
  '//lib/log:log4j',
]

PROVIDED = [
  '//gerrit-common:annotations',
  '//gerrit-common:server',
  '//gerrit-extension-api:api',
  '//gerrit-httpd:httpd',
  '//gerrit-lucene:lucene',
  '//gerrit-pgm:init',
  '//gerrit-reviewdb:server',
  '//gerrit-server:server',
  '//lib:gson',
  '//lib:jsch',
  '//lib/jgit/org.eclipse.jgit:jgit',
  '//lib/mina:sshd',
  '//lib:servlet-api-3_1',
]

java_binary(
  name = 'acceptance-framework',
  main_class = 'Dummy',
  runtime_deps = [':lib'],
  visibility = ['//visibility:public'],
)

java_library2(
  name = 'lib',
  srcs = SRCS,
  exported_deps = DEPS + [
    '//lib:truth',
  ],
  deps = PROVIDED + [ # We want these deps to be exported_deps
    '//lib:gwtorm',
    '//lib/guice:guice',
    '//lib/guice:guice-assistedinject',
    '//lib/guice:guice-servlet',
  ],
  visibility = ['//visibility:public'],
)
