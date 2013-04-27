java_binary(
  name = 'gerrit',
  main_class = 'Main',
  deps = [
    ':ui_optdbg',
    '//gerrit-server:server',
    '//gerrit-common:version',
  ],
)

java_library(
  name = 'ui_optdbg',
  resources = [genfile('gerrit-gwtui/resources/ui_optdbg.zip')],
  deps = ['//gerrit-gwtui:ui_optdbg'],
)
