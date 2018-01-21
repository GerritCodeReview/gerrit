load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "MAVEN_CENTRAL", "maven_jar")

_JGIT_VERS = "4.10.0.201712302008-r.24-gf3bb0e268"

_DOC_VERS = "4.10.0.201712302008-r"  # Set to _JGIT_VERS unless using a snapshot

JGIT_DOC_URL = "http://download.eclipse.org/jgit/site/" + _DOC_VERS + "/apidocs"

_JGIT_REPO = GERRIT  # Leave here even if set to MAVEN_CENTRAL.

# set this to use a local version.
# "/home/<user>/projects/jgit"
LOCAL_JGIT_REPO = ""

def jgit_repos():
  if LOCAL_JGIT_REPO:
    native.local_repository(
        name = "jgit",
        path = LOCAL_JGIT_REPO,
    )
  else:
    jgit_maven_repos()

def jgit_maven_repos():
    maven_jar(
        name = "jgit_lib",
        artifact = "org.eclipse.jgit:org.eclipse.jgit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "1813447ae544b38e36bda9f1599df5f7cff6cfac",
        src_sha1 = "f0bb1ac954ff529b4e1e01e65ceb0d46d949da9e",
        unsign = True,
    )
    maven_jar(
        name = "jgit_servlet",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "f7a88c3744f864587d50096ab99a58e09e4afd95",
        unsign = True,
    )
    maven_jar(
        name = "jgit_archive",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "af579bcc932fa26f3c4d4ae00e812dc50d50a355",
    )
    maven_jar(
        name = "jgit_junit",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "b5fc2330bf0418e3a0f773925c57497131f13380",
        unsign = True,
    )

def jgit_dep(name):
  mapping = {
      "@jgit_junit//jar": "@jgit//org.eclipse.jgit.junit:junit",
      "@jgit_lib//jar:src": "@jgit//org.eclipse.jgit:libjgit-src.jar",
      "@jgit_lib//jar": "@jgit//org.eclipse.jgit:jgit",
      "@jgit_servlet//jar":"@jgit//org.eclipse.jgit.http.server:jgit-servlet",
      "@jgit_archive//jar": "@jgit//org.eclipse.jgit.archive:jgit-archive",
  }

  if LOCAL_JGIT_REPO:
    return mapping[name]
  else:
    return name
