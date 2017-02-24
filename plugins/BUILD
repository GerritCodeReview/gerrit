load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:plugins.bzl",
     "CORE_PLUGINS",
     "CUSTOM_PLUGINS",
)

genrule2(
    name = "core",
    srcs = ["//plugins/%s:%s.jar" % (n, n) for n in CORE_PLUGINS + CUSTOM_PLUGINS],
    outs = ["core.zip"],
    cmd = "mkdir -p $$TMP/WEB-INF/plugins;" +
          "for s in $(SRCS) ; do " +
          "ln -s $$ROOT/$$s $$TMP/WEB-INF/plugins;done;" +
          "cd $$TMP;" +
          "zip -qr $$ROOT/$@ .",
    visibility = ["//visibility:public"],
)
