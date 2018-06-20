load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "MAVEN_CENTRAL", "STAGING", "maven_jar")

_JGIT_VERS = "5.0.0.201806131550-r"

_DOC_VERS = _JGIT_VERS  # Set to _JGIT_VERS unless using a snapshot

JGIT_DOC_URL = "http://download.eclipse.org/jgit/site/" + _DOC_VERS + "/apidocs"

_JGIT_REPO = MAVEN_CENTRAL  # Leave here even if set to MAVEN_CENTRAL.

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
        sha1 = "596edbf705924bd2defd9cfc83b29b1bceb56308",
        src_sha1 = "503a4c069baa672d3ff323d36c9b9a3a5edffc94",
        unsign = True,
    )
    maven_jar(
        name = "jgit-servlet",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "be2b42633f4973921e4c4b976f592f12f33bffd9",
        unsign = True,
    )
    maven_jar(
        name = "jgit-archive",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "3948643a6e07375ed0e28f35d75c0deb1cd183d8",
    )
    maven_jar(
        name = "jgit-junit",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "d57d749ad97f42d570236e7981f36458033bfda9",
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
