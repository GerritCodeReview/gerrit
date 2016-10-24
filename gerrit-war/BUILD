load('//tools/bzl:genrule2.bzl', 'genrule2')

java_library(
  name = 'init',
  srcs = glob(['src/main/java/**/*.java']),
  deps = [
    '//gerrit-cache-h2:cache-h2',
    '//gerrit-elasticsearch:elasticsearch',
    '//gerrit-extension-api:api',
    '//gerrit-gpg:gpg',
    '//gerrit-httpd:httpd',
    '//gerrit-lucene:lucene',
    '//gerrit-oauth:oauth',
    '//gerrit-openid:openid',
    '//gerrit-pgm:http',
    '//gerrit-pgm:init',
    '//gerrit-pgm:init-api',
    '//gerrit-pgm:util',
    '//gerrit-reviewdb:server',
    '//gerrit-server:server',
    '//gerrit-server/src/main/prolog:common',
    '//gerrit-sshd:sshd',
    '//lib:guava',
    '//lib:gwtorm',
    '//lib:servlet-api-3_1',
    '//lib/guice:guice',
    '//lib/guice:guice-servlet',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib/log:api',
  ],
  visibility = ['//visibility:public'],
)

genrule2(
  name = 'webapp_assets',
  cmd = 'cd gerrit-war/src/main/webapp; zip -qr $$ROOT/$@ .',
  srcs = glob(['src/main/webapp/**/*']),
  out = 'webapp_assets.zip',
  visibility = ['//visibility:public'],
)

java_import(
  name = 'log4j-config',
  jars = [':log4j-config__jar'],
  visibility = ['//visibility:public'],
)

genrule2(
  name = 'log4j-config__jar',
  cmd = 'cd gerrit-war/src/main/resources && zip -9Dqr $$ROOT/$@ .',
  srcs = ['src/main/resources/log4j.properties'],
  out = 'log4j-config.jar',
)

java_import(
  name = 'version',
  jars = [':gen_version'],
  visibility = ['//visibility:public'],
)

genrule2(
  name = 'gen_version',
  cmd = ' && '.join([
    'cd $$TMP',
    'mkdir -p com/google/gerrit/common',
    'cat $$ROOT/$(location //:version.txt) >com/google/gerrit/common/Version',
    'zip -9Dqr $$ROOT/$@ .',
  ]),
  tools = ['//:version.txt'],
  out = 'gen_version.jar',
)
