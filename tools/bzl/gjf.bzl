
def java_format_test(name, srcs, ws_name):
  native.sh_test(
    name = name,
    srcs = ["//tools/bzl:check_gjf.sh",],

    args = [ws_name,
            PACKAGE_NAME,
            "$(location @google_java_format//jar)",
            "$(location @local_jdk//:java)"] + srcs,
    data = [
      "//tools/bzl:check_gjf.sh",
      "@google_java_format//jar",
      "@local_jdk//:jdk-default",
      "@local_jdk//:java" ] +srcs
    )

def gen_java_format_tests(exceptions=None):
  srcs = []
  for r in native.existing_rules().values():
    if r["kind"] not in ("java_test", "java_library", "java_binary"):
      continue
    if len(r["srcs"]) == 0:
      continue

    for s in r["srcs"]:
      if s.startswith(":"):
        s = s[1:]
      if s.endswith(".java"):
        srcs.append(s)

  srcs = sorted(list(set(srcs)))
  java_format_test(
    name = "java_format_test",
    ws_name = "gerrit", #ugh
    srcs = srcs)
