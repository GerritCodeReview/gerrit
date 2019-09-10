_template = """
load("@bazel_skylib//lib:versions.bzl", "versions")

def check_bazel_version():
  versions.check(minimum_bazel_version = "{version}")
""".strip()

def _impl(repository_ctx):
    repository_ctx.symlink(Label("@//:.bazelversion"), ".bazelversion")
    bazelversion = repository_ctx.read(".bazelversion").strip()

    repository_ctx.file("BUILD", executable = False)

    repository_ctx.file("check.bzl", executable = False, content = _template.format(version = bazelversion))

bazelisk_version = repository_rule(implementation = _impl)
