load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

NAME = "com_googlesource_gerrit_bazlets"

# Bazlets version pin. Kept here, not in WORKSPACE, so bumping bazlets does not
# modify the Library-Compliance-gated WORKSPACE and therefore does not require a
# new LC+1 on every bump. This is symmetric with master (bzlmod), where the pin
# lives in tools/bazlets.MODULE.bazel rather than the root MODULE.bazel.
COMMIT = "156bae8bb0d329d8886f681c723979cbdb39d37c"

def load_bazlets(local_path = None):
    if not local_path:
        git_repository(
            name = NAME,
            remote = "https://gerrit.googlesource.com/bazlets",
            commit = COMMIT,
        )
    else:
        native.local_repository(
            name = NAME,
            path = local_path,
        )
