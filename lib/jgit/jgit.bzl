load("//tools/bzl:maven_jar.bzl", "ECLIPSE", "maven_jar")

_JGIT_VERS = "5.4.0.201905081430-m2"

_DOC_VERS = _JGIT_VERS  # Set to _JGIT_VERS unless using a snapshot

JGIT_DOC_URL = "https://download.eclipse.org/jgit/site/" + _DOC_VERS + "/apidocs"

_JGIT_REPO = ECLIPSE  # Leave here even if set to MAVEN_CENTRAL.

# set this to use a local version.
# "/home/<user>/projects/jgit"
LOCAL_JGIT_REPO = ""

def jgit_repos():
    if LOCAL_JGIT_REPO:
        native.local_repository(
            name = "jgit",
            path = LOCAL_JGIT_REPO,
        )
        jgit_maven_repos_dev()
    else:
        jgit_maven_repos()

def jgit_maven_repos_dev():
    # Transitive dependencies from JGit's WORKSPACE.
    maven_jar(
        name = "hamcrest-library",
        artifact = "org.hamcrest:hamcrest-library:1.3",
        sha1 = "4785a3c21320980282f9f33d0d1264a69040538f",
    )
    maven_jar(
        name = "jzlib",
        artifact = "com.jcraft:jzlib:1.1.1",
        sha1 = "a1551373315ffc2f96130a0e5704f74e151777ba",
    )

def jgit_maven_repos():
    maven_jar(
        name = "jgit-lib",
        artifact = "org.eclipse.jgit:org.eclipse.jgit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "b89c11dfa2d55d35c9d390cd856686b8e7c33b5a",
        src_sha1 = "3d664b3a39244dd8e6de3c52fc044c0877d8663a",
        unsign = True,
    )
    maven_jar(
        name = "jgit-servlet",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "f7acc5dfc06209ad3ab9b50deabaddc3ed85d620",
        unsign = True,
    )
    maven_jar(
        name = "jgit-archive",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "02cf4e8b0ec9a7579c629308ffe4b7bf8ab88d81",
    )
    maven_jar(
        name = "jgit-junit",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "51afdbf436f40e09080a57defa388036b8a1e68c",
        unsign = True,
    )

def jgit_dep(name):
    mapping = {
        "@jgit-archive//jar": "@jgit//org.eclipse.jgit.archive:jgit-archive",
        "@jgit-junit//jar": "@jgit//org.eclipse.jgit.junit:junit",
        "@jgit-lib//jar": "@jgit//org.eclipse.jgit:jgit",
        "@jgit-lib//jar:src": "@jgit//org.eclipse.jgit:libjgit-src.jar",
        "@jgit-servlet//jar": "@jgit//org.eclipse.jgit.http.server:jgit-servlet",
    }

    if LOCAL_JGIT_REPO:
        return mapping[name]
    else:
        return name
