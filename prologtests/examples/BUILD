package(default_visibility = ["//visibility:public"])

sh_test(
    name = "test_examples",
    srcs = ["run.sh"],
    data = glob(["*.pl"]) + ["//:gerrit.war"],
)
