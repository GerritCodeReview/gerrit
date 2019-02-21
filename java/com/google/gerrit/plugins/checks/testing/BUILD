package(
    default_testonly = True,
    default_visibility = ["//java/com/google/gerrit/plugins/checks:visibility"],
)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/plugins/checks:checks__plugin",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/testing",
        "//java/com/google/gerrit/truth",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//plugins:plugin-api",
    ],
)
