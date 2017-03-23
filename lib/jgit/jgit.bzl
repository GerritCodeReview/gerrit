load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "MAVEN_CENTRAL", "maven_jar")

JGIT_VERS = "4.6.0.201612231935-r.30-gd3148f300"

DOC_VERS = "4.6.0.201612231935-r" # Set to JGIT_VERS unless using a snapshot

JGIT_DOC_URL = "http://download.eclipse.org/jgit/site/" + DOC_VERS + "/apidocs"

JGIT_REPO = GERRIT # Leave here even if set to MAVEN_CENTRAL.

JGIT_SHA1 = "a2b5970b853f8fee64589fc1103c0ceb7677ba63"

JGIT_SRC_SHA1 = "765f955774c36c226aa41fba7c20119451de2db7"

JGIT_SERVLET_SHA1 = "d3aa54bd610db9a5c246aa8fef13989982c98628"

JGIT_ARCHIVE_SHA1 = "a728cf277396f1227c5a8dffcf5dee0188fc0821"

JGIT_JUNIT_SHA1 = "6c2b2f192c95d25a2e1576aee5d1169dd8bd2266"


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
        artifact = "org.eclipse.jgit:org.eclipse.jgit:" + JGIT_VERS,
        repository = JGIT_REPO,
        sha1 = JGIT_SHA1,
        src_sha1 = JGIT_SRC_SHA1,
        unsign = True,
    )

    maven_jar(
        name = "jgit_servlet",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + JGIT_VERS,
        repository = JGIT_REPO,
        sha1 = JGIT_SERVLET_SHA1,
        unsign = True,
    )

    maven_jar(
        name = "jgit_archive",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + JGIT_VERS,
        repository = JGIT_REPO,
        sha1 = JGIT_ARCHIVE_SHA1,
    )

    maven_jar(
        name = "jgit_junit",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + JGIT_VERS,
        repository = JGIT_REPO,
        sha1 = JGIT_JUNIT_SHA1,
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
