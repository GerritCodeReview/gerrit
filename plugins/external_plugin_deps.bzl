load("//tools/bzl:maven_jar.bzl", "maven_jar", "GERRIT")

JGIT_VERSION = '4.6.0.201612231935-r.30-gd3148f300'
REPO = GERRIT

def external_plugin_deps():
  maven_jar(
    name = 'jgit_http_apache',
    artifact = 'org.eclipse.jgit:org.eclipse.jgit.http.apache:' + JGIT_VERSION,
    sha1 = 'b9806f94d6b548c85a9ef96ef647b0f15b64927a',
    repository = REPO,
    unsign = True,
    exclude = [
      'about.html',
      'plugin.properties',
    ],
  )

  maven_jar(
    name = 'jgit_lfs',
    artifact = 'org.eclipse.jgit:org.eclipse.jgit.lfs:' + JGIT_VERSION,
    sha1 = '55cf48dd41732ded00d66f2f833e3b7346eb5e37',
    src_sha1 = 'fce0bceaece4c7885ffcd0d9405a524799b40db1',
    repository = REPO,
    unsign = True,
    exclude = [
      'about.html',
      'plugin.properties',
    ],
  )

  maven_jar(
    name = 'jgit_lfs_server',
    artifact = 'org.eclipse.jgit:org.eclipse.jgit.lfs.server:' + JGIT_VERSION,
    sha1 = '4d917afafe7888bba07607bfa7fcb06bb60fe7f1',
    src_sha1 = '1a31427d3a2940c661a16f51d1b2f96b37511fc6',
    repository = REPO,
    unsign = True,
    exclude = [
      'about.html',
      'plugin.properties',
    ],
  )
