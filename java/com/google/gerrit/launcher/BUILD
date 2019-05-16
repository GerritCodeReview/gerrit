# NOTE: GerritLauncher must be a single, self-contained class. Do not add any
# additional srcs or deps to this rule.
java_library(
    name = "launcher",
    srcs = ["GerritLauncher.java"],
    visibility = ["//visibility:public"],
)

# The root of the workspace is non-hermetic, but we need it for
# on-the-fly PolyGerrit updates.
genrule(
    name = "gen_root",
    outs = ["workspace-root.txt"],
    cmd = ("cat bazel-out/stable-status.txt | " +
           "grep STABLE_WORKSPACE_ROOT | cut -d ' ' -f 2 > $@"),
    stamp = 1,
    visibility = ["//visibility:public"],
)
