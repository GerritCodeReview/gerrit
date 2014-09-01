load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:plugin.bzl", "gerrit_plugin")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "polygerrit_plugin")

gerrit_plugin(
    name = "ac-gerrit",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: ac-gerrit",
        "Gerrit-Module: com.audiocodes.gwapp.plugins.acGerrit.Module",
        "Gerrit-HttpModule: com.audiocodes.gwapp.plugins.acGerrit.HttpModule",
    ],
    resource_jars = [":ac-polygerrit-static"],
    resources = glob(["src/main/resources/**/*"]),
)

genrule2(
    name = "ac-polygerrit-static",
    srcs = [":ac-polygerrit.nocache"],
    outs = ["ac-polygerrit-static.jar"],
    cmd = " && ".join([
        "mkdir $$TMP/static",
        "cp -r $(locations :ac-polygerrit.nocache) $$TMP/static",
        "cd $$TMP",
        "zip -Drq $$ROOT/$@ -g .",
    ]),
)

polygerrit_plugin(
    name = "ac-polygerrit.nocache",
    srcs = glob([
        "ac-polygerrit/*.html",
        "ac-polygerrit/*.js",
    ]),
    app = "plugin.html",
)
