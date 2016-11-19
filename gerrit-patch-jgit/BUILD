load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:gwt.bzl", "gwt_module")

SRC = "src/main/java/org/eclipse/jgit/"

gwt_module(
    name = "client",
    srcs = [
        SRC + "diff/Edit_JsonSerializer.java",
        SRC + "diff/ReplaceEdit.java",
    ],
    gwt_xml = SRC + "JGit.gwt.xml",
    visibility = ["//visibility:public"],
    deps = [
        ":Edit",
        "//lib:gwtjsonrpc",
        "//lib/gwt:user",
    ],
)

gwt_module(
    name = "Edit",
    srcs = [":jgit_edit_src"],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "jgit_edit_src",
    outs = ["edit.srcjar"],
    cmd = " && ".join([
        "unzip -qd $$TMP $(location @jgit//src) " +
        "org/eclipse/jgit/diff/Edit.java",
        "cd $$TMP",
        "zip -Dq $$ROOT/$@ org/eclipse/jgit/diff/Edit.java",
    ]),
    tools = ["@jgit//src"],
)

java_library(
    name = "server",
    srcs = [
        SRC + x
        for x in [
            "diff/EditDeserializer.java",
            "diff/ReplaceEdit.java",
            "internal/storage/file/WindowCacheStatAccessor.java",
            "lib/ObjectIdSerialization.java",
        ]
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:gson",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)

java_test(
    name = "jgit_patch_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    test_class = "org.eclipse.jgit.diff.EditDeserializerTest",
    visibility = ["//visibility:public"],
    deps = [
        ":server",
        "//lib:junit",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
