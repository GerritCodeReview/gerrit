
def java_format_test(name, srcs, ws_name):
  filtered = []
  for s in srcs:
    if s.startswith(":"):
      s = s[1:]
    if s.endswith(".java"):
      filtered.append(s)

  if not srcs:
      return

  native.sh_test(
    name = name,
    srcs = ["//tools/bzl:check_gjf.sh",],

    args = [ws_name,
            PACKAGE_NAME,
            "$(location @google_java_format//jar)",
            "$(location @local_jdk//:java)"] + filtered,
    data = [
      "//tools/bzl:check_gjf.sh",
      "@google_java_format//jar",
      "@local_jdk//:jdk-default",
      "@local_jdk//:java" ] +srcs
    )

# The workspace name. This should be injected generically, but it's
# not clear how that should work.
_WS_NAME = "gerrit"


def gen_java_format_tests(exceptions=None):
  """Invoke this as last rule to create a format for the BUILD package."""

  srcs = []
  for r in native.existing_rules().values():
    if r["kind"] not in ("java_test", "java_library", "java_binary"):
      continue

    srcs += r["srcs"]

  srcs = sorted(list(set(srcs)))
  java_format_test(
    name = "java_format_test",
    ws_name = _WS_NAME,
    srcs = srcs)

def java_library(name, **kwargs):
  """Load this to create a format test for a java_library."""
  srcs = kwargs.get("srcs", [])
  if srcs:
    java_format_test(name = name + "_format_test",
                     srcs = srcs,
                     ws_name = _WS_NAME)
  native.java_library(name=name, **kwargs)


def java_binary(name, **kwargs):
  """Load this to create a format test for a java_binary."""
  srcs = kwargs.get("srcs", [])
  if srcs:
    java_format_test(name = name + "_format_test",
                     srcs = srcs,
                     ws_name = _WS_NAME)
  native.java_binary(name=name, **kwargs)


def java_test(name, **kwargs):
  """Load this to create a format test for a java_test."""
  srcs = kwargs.get("srcs", [])
  if srcs:
    java_format_test(name = name + "_format_test",
                     srcs = srcs,
                     ws_name = _WS_NAME)
  native.java_test(name=name, **kwargs)
