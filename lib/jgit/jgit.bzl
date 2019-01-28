load("//tools/bzl:maven_jar.bzl", "MAVEN_CENTRAL", "maven_jar")
load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")

_JGIT_VERS = "5.3.1.201904271842-r"

_DOC_VERS = _JGIT_VERS  # Set to _JGIT_VERS unless using a snapshot

JGIT_DOC_URL = "https://download.eclipse.org/jgit/site/" + _DOC_VERS + "/apidocs"

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
    #    maven_jar(
    #        name = "jgit-lib",
    #        artifact = "org.eclipse.jgit:org.eclipse.jgit:" + _JGIT_VERS,
    #        repository = _JGIT_REPO,
    #        sha1 = "dba85014483315fa426259bc1b8ccda9373a624b",
    #        src_sha1 = "b2ddc76c39d81df716948a00d26faa35e11a0ddf",
    #        unsign = True,
    #    )
    java_import_external(
        name = "jgit-lib",
        jar_urls = ["https://github.com/davido/jgit/releases/download/v5.5.0/org.eclipse.jgit-5.5.0.jar"],
        jar_sha256 = "45628a95b9f8ec97fa7480d8292c550605fdfe651c9c6d98d869f317456a76a6",
    )
    maven_jar(
        name = "jgit-servlet",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "3287341fca859340a00b51cb5dd3b78b8e532b39",
        unsign = True,
    )
    maven_jar(
        name = "jgit-archive",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "3585027e83fb44a5de2c10ae9ddbf976593bf080",
    )
    maven_jar(
        name = "jgit-junit",
        artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + _JGIT_VERS,
        repository = _JGIT_REPO,
        sha1 = "3d9ba7e610d6ab5d08dcb1e4ba448b592a34de77",
        unsign = True,
    )

def jgit_dep(name):
    mapping = {
        "@jgit-archive//jar": "@jgit//org.eclipse.jgit.archive:jgit-archive",
        "@jgit-junit//jar": "@jgit//org.eclipse.jgit.junit:junit",
        "@jgit-lib//jar": "@jgit//org.eclipse.jgit:jgit",
        "@jgit-servlet//jar": "@jgit//org.eclipse.jgit.http.server:jgit-servlet",
    }

    if LOCAL_JGIT_REPO:
        return mapping[name]
    else:
        return name
