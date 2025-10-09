# npm packages are split into different node_modules directories based on their
# usage.
# 1. @npm (node_modules) - contains packages to run tests, check code, etc...
#    It is expected that @npm is used ONLY to run tools. No packages from @npm
#    are used by other code in gerrit.
# 2. @tools_npm (tools/node_tools/node_modules) - the tools/node_tools folder
#    contains self-written tools which are run for building and/or testing. The
#    @tools_npm directory contains all the packages needed to run this tools.
# 3. @ui_npm (polygerrit-ui/app/node_modules) - packages with source code which
#    are necessary to run polygerrit and to bundle it. Only code from these
#    packages can be included in the final bundle for polygerrit. @ui_npm folder
#    must not have devDependencies. All devDependencies must be placed in
#    @ui_dev_npm.
# 4. @ui_dev_npm (polygerrit-ui/node_modules) - devDependencies for polygerrit.
#    The packages from these folder can be used for testing, but must not be
#    included in the final bundle.
# 5. @plugins_npm (plugins/node_modules) - plugin dependencies for polygerrit
#    plugins. The packages here are expected to be used in plugins.
# Note: separation between @ui_npm and @ui_dev_npm is necessary because with
#    rules_nodejs we can't generate two external repositories from the same
#    package.json. At the same time we want to avoid accidental usages of code
#    from devDependencies in polygerrit bundle.
workspace(
    name = "gerrit",
)

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")
load("//tools:nongoogle.bzl", "declare_nongoogle_deps")
load("//tools:deps.bzl", "CAFFEINE_VERS", "java_dependencies")

http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "a1295b168f183218bc88117cf00674bcd102498f294086ff58318f830dd9d9d1",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/5.8.5/rules_nodejs-5.8.5.tar.gz"],
)

load("@build_bazel_rules_nodejs//:repositories.bzl", "build_bazel_rules_nodejs_dependencies")

build_bazel_rules_nodejs_dependencies()

# This is required just because we have a dependency on @bazel/concatjs.
# We don't actually use any of this web_testing stuff.
# TODO: Remove this dependency.
http_archive(
    name = "io_bazel_rules_webtesting",
    sha256 = "e9abb7658b6a129740c0b3ef6f5a2370864e102a5ba5ffca2cea565829ed825a",
    urls = [
        "https://github.com/bazelbuild/rules_webtesting/releases/download/0.3.5/rules_webtesting.tar.gz",
    ],
)

# TODO: Remove this, see comments on `io_bazel_rules_webtesting`.
load("@io_bazel_rules_webtesting//web:repositories.bzl", "web_test_repositories")

# TODO: Remove this, see comments on `io_bazel_rules_webtesting`.
web_test_repositories()

# TODO: Remove this, see comments on `io_bazel_rules_webtesting`.
load("@io_bazel_rules_webtesting//web/versioned:browsers-0.3.3.bzl", "browser_repositories")

# TODO: Remove this, see comments on `io_bazel_rules_webtesting`.
browser_repositories(
    chromium = True,
    firefox = True,
)

declare_nongoogle_deps()

load("//tools:defs.bzl", "gerrit_init")

gerrit_init()

# Java-Prettify external repository consumed from git submodule
local_repository(
    name = "java-prettify",
    path = "modules/java-prettify",
)

# JGit external repository consumed from git submodule
local_repository(
    name = "jgit",
    path = "modules/jgit",
)

java_dependencies()

CAFFEINE_GUAVA_SHA256 = "6e48965614557ba4d3c55a197e20c38f23a20032ef8aace37e95ed64d2ebc9a6"

# TODO(davido): Rename guava.jar to caffeine-guava.jar on fetch to prevent potential
# naming collision between caffeine guava adapter and guava library itself.
# Remove this renaming procedure, once this upstream issue is fixed:
# https://github.com/ben-manes/caffeine/issues/364.
http_file(
    name = "caffeine-guava-renamed",
    canonical_id = "caffeine-guava-" + CAFFEINE_VERS + ".jar-" + CAFFEINE_GUAVA_SHA256,
    downloaded_file_path = "caffeine-guava-" + CAFFEINE_VERS + ".jar",
    sha256 = CAFFEINE_GUAVA_SHA256,
    urls = [
        "https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/guava/" +
        CAFFEINE_VERS +
        "/guava-" +
        CAFFEINE_VERS +
        ".jar",
    ],
)

load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "yarn_install")

NODE_20_REPO = {
    "20.19.5-darwin_arm64": ("node-v20.19.5-darwin-arm64.tar.gz", "node-v20.19.5-darwin-arm64", "e0065c61f340e85106a99c4b54746c5cee09d59b08c5712f67f99e92aa44995d"),
    "20.19.5-darwin_amd64": ("node-v20.19.5-darwin-x64.tar.gz", "node-v20.19.5-darwin-x64", "c52e7fb0709dbe63a4cbe08ac8af3479188692937a7bd8e776e0eedfa33bb848"),
    "20.19.5-linux_arm64": ("node-v20.19.5-linux-arm64.tar.xz", "node-v20.19.5-linux-arm64", "c957f29eb4e341903520caf362534f0acd1db7be79c502ae8e283994eed07fe1"),
    "20.19.5-linux_ppc64le": ("node-v20.19.5-linux-ppc64le.tar.xz", "node-v20.19.5-linux-ppc64le", "51343cacf5cdf5c4b5e93e919d19dd373d6ef43d5f2c666eae299f26e31d08b5"),
    "20.19.5-linux_s390x": ("node-v20.19.5-linux-s390x.tar.xz", "node-v20.19.5-linux-s390x", "b32616b705cd0ddbb230b95c693e3d7a37becc2ced9bcadea8dc824cceed6be0"),
    "20.19.5-linux_amd64": ("node-v20.19.5-linux-x64.tar.xz", "node-v20.19.5-linux-x64", "d8dab549b09672b03356aa2257699f3de3b58c96e74eb26a8b495fbdc9cf6fbe"),
    "20.19.5-windows_amd64": ("node-v20.19.5-win-x64.zip", "node-v20.19.5-win-x64", "bc032628d77d206ffa7f133518a6225a9c5d6d9210ead30d67e294ff37044bda"),
}

node_repositories(
    node_repositories = NODE_20_REPO,
    node_version = "20.19.5",
    yarn_version = "1.22.19",
)

yarn_install(
    name = "npm",
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:package.json",
    package_path = "",
    symlink_node_modules = True,
    yarn_lock = "//:yarn.lock",
)

yarn_install(
    name = "ui_npm",
    args = [
        "--prod",
        # By default, yarn install all optional dependencies.
        # In some cases, it installs a lot of additional dependencies which
        # are not required (for example, "resemblejs" has one optional
        # dependencies "canvas" that leads to tens of additional dependencies).
        # Each additional dependency requires a license even if it is not used
        # in our code.  We want to ensure that all optional dependencies are
        # explicitly added to package.json.
        "--ignore-optional",
    ],
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:polygerrit-ui/app/package.json",
    package_path = "polygerrit-ui/app",
    symlink_node_modules = True,
    yarn_lock = "//:polygerrit-ui/app/yarn.lock",
)

yarn_install(
    name = "ui_dev_npm",
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:polygerrit-ui/package.json",
    package_path = "polygerrit-ui",
    symlink_node_modules = True,
    yarn_lock = "//:polygerrit-ui/yarn.lock",
)

yarn_install(
    name = "tools_npm",
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:tools/node_tools/package.json",
    package_path = "tools/node_tools",
    symlink_node_modules = True,
    yarn_lock = "//:tools/node_tools/yarn.lock",
)

yarn_install(
    name = "plugins_npm",
    args = ["--prod"],
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:plugins/package.json",
    package_path = "plugins",
    symlink_node_modules = True,
    yarn_lock = "//:plugins/yarn.lock",
)

external_plugin_deps()
