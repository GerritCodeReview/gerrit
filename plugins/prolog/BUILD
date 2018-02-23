load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "prolog",
    srcs = glob(["java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: Prolog rules engine",
        "Gerrit-Module: com.google.gerrit.plugins.PrologModule",
        "Implementation-Title: Prolog rules engine plugin",
    ],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/pgm",
        "//java/com/google/gerrit/pgm/util",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/schema",
        "//lib:gwtorm",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
        "//plugins/prolog/lib/prolog:cafeteria",
        "//plugins/prolog/lib/prolog:compiler",
        "//plugins/prolog/lib/prolog:runtime",
    ],
)
