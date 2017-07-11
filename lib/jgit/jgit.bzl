load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "MAVEN_CENTRAL", "maven_jar")

# DO NOT SUBMIT: use submitted version
_JGIT_VERS = "4.8.0.201706111038-r.32-g1e9639fae"

_DOC_VERS = _JGIT_VERS # Set to _JGIT_VERS unless using a snapshot

JGIT_DOC_URL = "http://download.eclipse.org/jgit/site/" + _DOC_VERS + "/apidocs"

_JGIT_REPO = GERRIT # Leave here even if set to MAVEN_CENTRAL.

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
        sha1 = "d74fb68ccfedc73415f6af539a9ffbe8d675e22d",
        src_sha1 = "ec36ecdd3e1c98ac31d7cc3e99af8f1d235099b7",
        unsign = True,
    )
    maven_jar(
        name = "jgit_servlet",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "243cc3bd18858d86180ae817982861a49110afaa",
        unsign = True,
    )
    maven_jar(
        name = "jgit_archive",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "8d2d2c5e9469b1c1c880367ba49f7210dda4e039",
    )
    maven_jar(
        name = "jgit_junit",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "665e7a55e6d3815a626389c0b704b20f380ff7b0",
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
