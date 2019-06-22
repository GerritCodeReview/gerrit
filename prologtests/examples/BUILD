package(default_visibility = ["//visibility:public"])

DUMMY = ["dummy.sh"]

# Enable prologtests on newer Java versions again, when this Bazel bug is fixed:
# https://github.com/bazelbuild/bazel/issues/9391
sh_test(
    name = "test_examples",
    srcs = select({
        "//:java11": DUMMY,
        "//:java_next": DUMMY,
        "//conditions:default": ["run.sh"],
    }),
    data = glob(["*.pl"]) + ["//:gerrit.war"],
)
