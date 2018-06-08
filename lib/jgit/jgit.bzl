load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "MAVEN_CENTRAL", "maven_jar")

_JGIT_VERS = "4.11.0.201803080745-r.93-gcbb2e65db"

_DOC_VERS = "4.11.0.201803080745-r"  # Set to _JGIT_VERS unless using a snapshot

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
        name = "jgit-lib",
        artifact = "org.eclipse.jgit:org.eclipse.jgit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "265a39c017ecfeed7e992b6aaa336e515bf6e157",
        src_sha1 = "e9d801e17afe71cdd5ade84ab41ff0110c3f28fd",
        unsign = True,
    )
    maven_jar(
        name = "jgit-servlet",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "0d68f62286b5db759fdbeb122c789db1f833a06a",
        unsign = True,
    )
    maven_jar(
        name = "jgit-archive",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "4cc3ed2c42ee63593fd1b16215fcf13eeefb833e",
    )
    maven_jar(
        name = "jgit-junit",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "6f1bcc9ac22b31b5a6e1e68c08283850108b900c",
        unsign = True,
    )

def jgit_dep(name):
  mapping = {
      "@jgit-junit//jar": "@jgit//org.eclipse.jgit.junit:junit",
      "@jgit-lib//jar:src": "@jgit//org.eclipse.jgit:libjgit-src.jar",
      "@jgit-lib//jar": "@jgit//org.eclipse.jgit:jgit",
      "@jgit-servlet//jar":"@jgit//org.eclipse.jgit.http.server:jgit-servlet",
      "@jgit-archive//jar": "@jgit//org.eclipse.jgit.archive:jgit-archive",
  }

  if LOCAL_JGIT_REPO:
    return mapping[name]
  else:
    return name
