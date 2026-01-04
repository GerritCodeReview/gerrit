"""
Bazel definitions for tools.
"""

load("@bazel_features//:deps.bzl", "bazel_features_deps")

def gerrit_init():
    """
    Initialize the WORKSPACE for gerrit targets
    """

    bazel_features_deps()
