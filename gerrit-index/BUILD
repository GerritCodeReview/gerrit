java_library(
  name = 'index',
  srcs = glob(['src/main/java/**/*.java']),
  deps = [
    '//gerrit-extension-api:api',
    '//gerrit-server:server',
    '//gerrit-patch-jgit:server',
    '//lib/guice:guice',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib:guava',
  ],
  visibility = ['//visibility:public'],
)
