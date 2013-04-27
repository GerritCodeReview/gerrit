# TODO(sop): daemon --console-log broken

include_defs('//lib/war.defs')

war(
  name = 'gerrit',
  libs = [
    '//gerrit-common:version',
    '//gerrit-war:init',
    '//gerrit-war:log4j-config',
    '//lib:postgresql',
    '//lib/log:impl_log4j',
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
