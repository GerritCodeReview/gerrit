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
load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")
load("//tools:nongoogle.bzl", "declare_nongoogle_deps")
load("//tools:deps.bzl", "CAFFEINE_VERS", "java_dependencies")

http_archive(
    name = "rules_java",
    sha256 = "4018e97c93f97680f1650ffd2a7530245b864ac543fd24fae8c02ba447cb2864",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/7.3.1/rules_java-7.3.1.tar.gz",
    ],
)

http_archive(
    name = "platforms",
    sha256 = "3a561c99e7bdbe9173aa653fd579fe849f1d8d67395780ab4770b1f381431d51",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/platforms/releases/download/0.0.7/platforms-0.0.7.tar.gz",
        "https://github.com/bazelbuild/platforms/releases/download/0.0.7/platforms-0.0.7.tar.gz",
    ],
)

http_archive(
    name = "ubuntu2204_jdk17",
    sha256 = "8ea82b81c9707e535ff93ef5349d11e55b2a23c62bcc3b0faaec052144aed87d",
    strip_prefix = "rbe_autoconfig-5.1.0",
    urls = [
        "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v5.1.0.tar.gz",
        "https://github.com/davido/rbe_autoconfig/releases/download/v5.1.0/v5.1.0.tar.gz",
    ],
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "75be42bd736f4df6d702a0e4e4d30de9ee40eac024c4b845d17ae4cc831fe4ae",
    strip_prefix = "protobuf-21.7",
    urls = [
        "https://github.com/protocolbuffers/protobuf/archive/v21.7.tar.gz",
    ],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

http_archive(
    name = "rules_nodejs",
    patch_args = ["-p1"],
    patches = ["//tools:rules_nodejs-5.8.4-node_versions.bzl.patch"],
    sha256 = "8fc8e300cb67b89ceebd5b8ba6896ff273c84f6099fc88d23f24e7102319d8fd",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/5.8.4/rules_nodejs-core-5.8.4.tar.gz"],
)

http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "709cc0dcb51cf9028dd57c268066e5bc8f03a119ded410a13b5c3925d6e43c48",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/5.8.4/rules_nodejs-5.8.4.tar.gz"],
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

register_toolchains("//tools:error_prone_warnings_toolchain_java11_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java21_definition")

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

declare_nongoogle_deps()

load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "yarn_install")

node_repositories(
    node_version = "20.9.0",
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
