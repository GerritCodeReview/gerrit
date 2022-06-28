load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

# TODO(davido): This was only needed as own rule, to provide a dedicated
# tag to skip Git version v2 protocol tests. That was particularly
# needed for RBE, because this test assumes that git client version is
# at least 2.17.1. Once Bazel docker image for Ubuntu 20.04 is available
# and we removed our own RBE docker image, we can merge this rule with
# the other rules in this package.
acceptance_tests(
    srcs = ["GitProtocolV2IT.java"],
    group = "protocol-v2",
    labels = ["git-protocol-v2"],
)

# This rule can be also merged with the other tests in this package.
acceptance_tests(
    srcs = ["UploadArchiveIT.java"],
    group = "upload-archive",
    labels = ["git-upload-archive"],
)

acceptance_tests(
    srcs = glob(
        ["*.java"],
        exclude = [
            "GitProtocolV2IT.java",
            "UploadArchiveIT.java",
        ],
    ),
    group = "git_tests",
    labels = ["git"],
)
