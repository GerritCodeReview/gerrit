load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools/bzl:maven_jar.bzl", "maven_jar")

_JGIT_VERS = "3b5a4416aaf69f9b5184e332c54ad2997c0b2fe9"
_JGIT_SHA256 = "2cddd8ae97bea560c876880b1f227d48647e648c18ad65e5b87161f2ca5b021b"

_DOC_VERS = "5.5.0.201909110433-r"  # Set to _JGIT_VERS unless using a snapshot

JGIT_DOC_URL = "https://download.eclipse.org/jgit/site/" + _DOC_VERS + "/apidocs"

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
        # TODO(davido): Replace github URL with
        # https://git.eclipse.org/r/plugins/gitiles/jgit
        # when this JGit bug is fixed:
        # https://bugs.eclipse.org/bugs/show_bug.cgi?id=548312
        # and deployed on git.eclipse.org.
        http_archive(
            name = "jgit",
            strip_prefix = "jgit-" + _JGIT_VERS,
            sha256 = _JGIT_SHA256,
            urls = [
                "https://github.com/eclipse/jgit/archive/" + _JGIT_VERS + ".tar.gz",
            ],
        )
        jgit_maven_repos()

def jgit_maven_repos():
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
