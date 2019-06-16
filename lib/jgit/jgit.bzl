load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools/bzl:maven_jar.bzl", "maven_jar")

_JGIT_VERS = "8cd07cb8157eec75099cb93c25d6daa9d5e6e0bc"

_DOC_VERS = _JGIT_VERS  # Set to _JGIT_VERS unless using a snapshot

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
        # wenn this JGit bug is fixed:
        # https://bugs.eclipse.org/bugs/show_bug.cgi?id=548312
        http_archive(
            name = "jgit",
            strip_prefix = "jgit-" + _JGIT_VERS,
            sha256 = "49d300977bd26384206aeb6aeec11b35c0e9dfcfd5b244e99266a21d9d8e3aa9",
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
