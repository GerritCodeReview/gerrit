# Maven style API version (e.g. '2.x-SNAPSHOT').
# Used by :api_install and :api_deploy targets
# when talking to the destination repository.
#
GERRIT_VERSION = "2.16-SNAPSHOT"

def check_version(x):
    if native.bazel_version < x:
        fail("Current Bazel version is {}, expected at least {}".format(native.bazel_version, x))
