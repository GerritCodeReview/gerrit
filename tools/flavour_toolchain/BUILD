load("@com_googlesource_gerrit_bazlets//tools:servlet_flavour.bzl", "servlet_flavour_toolchain")

# Gerrit's servlet-flavour toolchain impls: one bundle per flavour, resolved
# by //flags:flavour (registered in //MODULE.bazel). EE11 is the only flavour
# since the EE8 retirement; the next servlet generation (EE12) arrives as
# another servlet_flavour_toolchain + toolchain pair here.
servlet_flavour_toolchain(
    name = "servlet_ee11_impl",
    flavour = "ee11",
    gitiles_servlet = "@external_deps//:com_google_gitiles_gitiles_servlet",
    guice = "//lib/guice:guice-ee11",
    guice_assistedinject = "//lib/guice:guice-assistedinject-ee11",
    guice_servlet = "//lib/guice:guice-servlet-ee11",
    jetty = "//lib/jetty:servlet-ee11",
    jgit_servlet = "//lib:jgit-servlet-ee11",
    servlet_api = "//lib:servlet-api-ee11",
    servlet_api_runtime = "//lib:servlet-api-without-neverlink-ee11",
)

# ee11 toolchain: usable only when //flags:flavour=ee11 (via target_settings).
toolchain(
    name = "servlet_ee11_toolchain",
    target_settings = ["@com_googlesource_gerrit_bazlets//flags:ee11"],
    toolchain = ":servlet_ee11_impl",
    toolchain_type = "@com_googlesource_gerrit_bazlets//flags:servlet_flavour_toolchain_type",
)
