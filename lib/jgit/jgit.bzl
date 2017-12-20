load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "MAVEN_CENTRAL", "maven_jar")

_JGIT_VERS = "4.9.2.201712150930-r.3-g43ef5dabf"

_DOC_VERS = "4.9.2.201712150930-r"  # Set to _JGIT_VERS unless using a snapshot

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
        sha1 = "3f6a1002069be91d99e1b356193aac5bbe5b3da3",
        src_sha1 = "4fbbcd1e2f474917dd0ddbfef2580f474daf4dbd",
        unsign = True,
    )
    maven_jar(
        name = "jgit_servlet",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "78425749a618dd82da8dcf19ef9fd14e4318315b",
        unsign = True,
    )
    maven_jar(
        name = "jgit_archive",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "884933af30be5c64187838e43764e0e19309f850",
    )
    maven_jar(
        name = "jgit_junit",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "d7c24fec0a23842a03a6eea592a07fbd1448e783",
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
