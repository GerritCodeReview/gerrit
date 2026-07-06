load("@com_googlesource_gerrit_bazlets//tools:servlet_flavour.bzl", "servlet_flavour_toolchain")

# Gerrit's servlet-flavour toolchain impls: one bundle per flavour, resolved by
# //flags:flavour (registered in //MODULE.bazel). Each carries the flavour's
# servlet API + Jetty adapter + Guice cluster + JGit/Gitiles servlet.
servlet_flavour_toolchain(
    name = "servlet_ee8_impl",
    flavour = "ee8",
    gitiles_servlet = "@external_deps//:com_google_gitiles_gitiles_servlet",
    guice = "//lib/guice:guice-ee8",
    guice_assistedinject = "//lib/guice:guice-assistedinject-ee8",
    guice_servlet = "//lib/guice:guice-servlet-ee8",
    jetty = "//lib/jetty:servlet-ee8",
    jgit_servlet = "//lib:jgit-servlet-ee8",
    servlet_api = "//lib:servlet-api-ee8",
    servlet_api_runtime = "//lib:servlet-api-without-neverlink-ee8",
)

servlet_flavour_toolchain(
    name = "servlet_ee10_impl",
    flavour = "ee10",
    gitiles_servlet = "@external_deps_ee10//:com_google_gitiles_gitiles_servlet",
    guice = "//lib/guice:guice-ee10",
    guice_assistedinject = "//lib/guice:guice-assistedinject-ee10",
    guice_servlet = "//lib/guice:guice-servlet-ee10",
    jetty = "//lib/jetty:servlet-ee10",
    jgit_servlet = "//lib:jgit-servlet-ee10",
    servlet_api = "//lib:servlet-api-ee10",
    servlet_api_runtime = "//lib:servlet-api-without-neverlink-ee10",
)

# ee10 toolchain: usable only when //flags:flavour=ee10 (via target_settings).
toolchain(
    name = "servlet_ee10_toolchain",
    target_settings = ["@com_googlesource_gerrit_bazlets//flags:ee10"],
    toolchain = ":servlet_ee10_impl",
    toolchain_type = "@com_googlesource_gerrit_bazlets//flags:servlet_flavour_toolchain_type",
)

# ee8 toolchain: usable only when //flags:flavour=ee8 (the default). Explicit
# per-flavour target_settings mean exactly one toolchain matches -- no reliance
# on registration precedence, and adding ee11/ee12 is just another such pair.
toolchain(
    name = "servlet_ee8_toolchain",
    target_settings = ["@com_googlesource_gerrit_bazlets//flags:ee8"],
    toolchain = ":servlet_ee8_impl",
    toolchain_type = "@com_googlesource_gerrit_bazlets//flags:servlet_flavour_toolchain_type",
)
