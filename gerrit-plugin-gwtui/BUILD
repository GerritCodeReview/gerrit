package(default_visibility = ['//visibility:public'])
load('//tools/bzl:java.bzl', 'java_library2')

SRCS = glob(['src/main/java/com/google/gerrit/**/*.java'])
DEPS = ['//lib/gwt:user-neverlink']

java_binary(
  name = 'gwtui-api',
  main_class = 'Dummy',
  runtime_deps = [
    ':gwtui-api-lib',
    '//gerrit-gwtui-common:client-lib',
  ],
)

java_library2(
  name = 'gwtui-api-lib',
  srcs = SRCS,
  resources = glob(['src/main/**/*']),
  exported_deps = ['//gerrit-gwtui-common:client-lib'],
  deps = DEPS + ['//lib/gwt:dev-neverlink'], # we want this to be exported deps
)

java_library2(
  name = 'gwtui-api-lib-neverlink',
  srcs = SRCS,
  resources = glob(['src/main/**/*']),
  exported_deps = ['//gerrit-gwtui-common:client-lib'],
  neverlink = 1, # we want this to be exported deps
  deps = DEPS + ['//lib/gwt:dev'],
)

java_binary(
  name = 'gwtui-api-source',
  main_class = 'Dummy',
  runtime_deps = [
    ':libgwtui-api-lib-src.jar',
    '//gerrit-gwtexpui:client-src-lib',
    '//gerrit-gwtui-common:libclient-lib-src.jar',
  ],
)

load('//tools/bzl:javadoc.bzl', 'java_doc')

java_doc(
  name = 'gwtui-api-javadoc',
  title = 'Gerrit Review GWT Extension API Documentation',
  pkgs = [
    'com.google.gerrit.plugin',
    'com.google.gwtexpui.clippy',
    'com.google.gwtexpui.globalkey',
    'com.google.gwtexpui.safehtml',
    'com.google.gwtexpui.user',
  ],
  libs = DEPS + [
    ':gwtui-api-lib',
    '//lib:gwtjsonrpc',
    '//lib:gwtorm_client',
    '//lib/gwt:dev',
    '//gerrit-gwtui-common:client-lib',
    '//gerrit-common:client',
    '//gerrit-reviewdb:client',
  ],
)
