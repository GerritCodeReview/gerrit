include_defs('//lib/maven.defs')

API_VERSION = '2.9-SNAPSHOT'
REPO = MAVEN_LOCAL

gerrit_plugin(
  name = 'server-config',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: server-config',
    'Gerrit-HttpModule: com.googlesource.gerrit.plugins.serverconfig.HttpModule',
  ]
)
