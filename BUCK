include_defs('//lib/war.defs')

# TODO(sop): strip javax/servlet
# TODO(sop): strip org/bouncycastle
# TODO(sop): strip com/googlecode/prolog_cafe/compiler?
# TODO(sop): where did org/w3c/dom come from? xerces?

war(
  name = 'gerrit',
  libs = [
    '//gerrit-war:init',
    '//gerrit-war:log4j-config',
  ],
  pgmlibs = [
    '//gerrit-pgm:pgm',
  ],
  webapp_jars = [
    '//gerrit-main:main_bin',
  ],
  webapp_zips = [
    '//gerrit-war:webapp_assets',
    '//gerrit-gwtui:ui_optdbg',
  ],
)

java_binary(
  name = 'gerrit_deploy',
  main_class = 'Main',
  deps = [
    '//gerrit-common:version',
    '//gerrit-gwtui:lib_optdbg',
    '//gerrit-pgm:pgm',
    '//gerrit-server:common_rules',
    '//gerrit-server:server',
    '//lib:postgresql',
    '//lib/log:impl_log4j',
  ],
)
