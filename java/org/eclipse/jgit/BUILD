load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "client",
    srcs = [
        "diff/Edit_JsonSerializer.java",
        "diff/ReplaceEdit.java",
    ],
    gwt_xml = "JGit.gwt.xml",
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
        "unzip -qd $$TMP $(location //lib/jgit/org.eclipse.jgit:jgit-source) " +
        "org/eclipse/jgit/diff/Edit.java",
        "cd $$TMP",
        "zip -Dq $$ROOT/$@ org/eclipse/jgit/diff/Edit.java",
    ]),
    tools = ["//lib/jgit/org.eclipse.jgit:jgit-source"],
)

java_library(
    name = "server",
    srcs = [
        "diff/EditDeserializer.java",
        "diff/ReplaceEdit.java",
        "internal/storage/file/WindowCacheStatAccessor.java",
        "lib/ObjectIdSerialization.java",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:gson",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
