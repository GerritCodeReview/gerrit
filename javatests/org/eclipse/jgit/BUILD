java_test(
    name = "jgit_patch_tests",
    srcs = glob(["**/*.java"]),
    test_class = "org.eclipse.jgit.diff.EditDeserializerTest",
    visibility = ["//visibility:public"],
    deps = [
        "//java/org/eclipse/jgit:server",
        "//lib:junit",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
