# npm packages are split into different node_modules directories based on their usage.
# 1. /node_modules (referenced as @npm) - contains packages to run tests, check code, etc...
#    It is expected that @npm is used ONLY to run tools. No packages from @npm are used by
#    other code in gerrit.
# 2. @tools_npm (tools/node_tools/node_modules) - the tools/node_tools folder contains self-written tools
#    which are run for building and/or testing. The @tools_npm directory contains all the packages needed to
#    run this tools.
# 3. @ui_npm (polygerrit-ui/app/node_modules) - packages with source code which are necessary to run polygerrit
#    and to bundle it. Only code from these packages can be included in the final bundle for polygerrit.
#    @ui_npm folder must not have devDependencies. All dev dependencies must be placed in @ui_dev_npm
# 4. @ui_dev_npm (polygerrit-ui/node_modules) - devDependencies for polygerrit. The packages from these
#    folder can be used for testing, but must not be included in the final bundle.
# 5. @plugins_npm (plugins/node_modules) - plugin dependencies for polygerrit plugins.
#    The packages here are expected to be used in plugins.
# Note: separation between @ui_npm and @ui_dev_npm is necessary because with bazel we can't generate
#    two managed directories from the same package.json. At the same time we want to avoid accidental
#    usages of code from devDependencies in polygerrit bundle.
workspace(
    name = "gerrit",
    managed_directories = {
        "@npm": ["node_modules"],
        "@ui_npm": ["polygerrit-ui/app/node_modules"],
        "@ui_dev_npm": ["polygerrit-ui/node_modules"],
        "@tools_npm": ["tools/node_tools/node_modules"],
        "@plugins_npm": ["plugins/node_modules"],
    },
)

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")
load("//tools:nongoogle.bzl", "TESTCONTAINERS_VERSION", "declare_nongoogle_deps")
load("//tools:deps.bzl", "CAFFEINE_VERS", "java_dependencies")

http_archive(
    name = "bazel_toolchains",
    sha256 = "1adf7a8e9901287c644dcf9ca08dd8d67a69df94bedbd57a841490a84dc1e9ed",
    strip_prefix = "bazel-toolchains-5.0.0",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/v5.0.0.tar.gz",
        "https://github.com/bazelbuild/bazel-toolchains/archive/v5.0.0.tar.gz",
    ],
)

load("@bazel_toolchains//rules:rbe_repo.bzl", "rbe_autoconfig")

# Creates a default toolchain config for RBE.
rbe_autoconfig(
    name = "rbe_jdk11",
    java_home = "/usr/lib/jvm/11.29.3-ca-jdk11.0.2/reduced",
    use_checked_in_confs = "Force",
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "d0f5f605d0d656007ce6c8b5a82df3037e1d8fe8b121ed42e536f569dec16113",
    strip_prefix = "protobuf-3.14.0",
    urls = [
        "https://github.com/protocolbuffers/protobuf/archive/v3.14.0.tar.gz",
    ],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "10f534e1c80f795cffe1f2822becd4897754d18564612510c59b3c73544ae7c6",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/3.5.0/rules_nodejs-3.5.0.tar.gz"],
)

http_archive(
    name = "rules_pkg",
    sha256 = "038f1caa773a7e35b3663865ffb003169c6a71dc995e39bf4815792f385d837d",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_pkg/releases/download/0.4.0/rules_pkg-0.4.0.tar.gz",
        "https://github.com/bazelbuild/rules_pkg/releases/download/0.4.0/rules_pkg-0.4.0.tar.gz",
    ],
)

load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")

rules_pkg_dependencies()

# Golang support for PolyGerrit local dev server.
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "4d838e2d70b955ef9dd0d0648f673141df1bc1d7ecf5c2d621dcc163f47dd38a",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.24.12/rules_go-v0.24.12.tar.gz",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.24.12/rules_go-v0.24.12.tar.gz",
    ],
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains()

http_archive(
    name = "bazel_gazelle",
    sha256 = "222e49f034ca7a1d1231422cdb67066b885819885c356673cb1f72f748a3c9d4",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.22.3/bazel-gazelle-v0.22.3.tar.gz",
        "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.22.3/bazel-gazelle-v0.22.3.tar.gz",
    ],
)

load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies", "go_repository")

gazelle_dependencies()

# Dependencies for PolyGerrit local dev server.
go_repository(
    name = "com_github_howeyc_fsnotify",
    commit = "441bbc86b167f3c1f4786afae9931403b99fdacf",
    importpath = "github.com/howeyc/fsnotify",
)

register_toolchains("//tools:error_prone_warnings_toolchain_java11_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

# JGit external repository consumed from git submodule
local_repository(
    name = "jgit",
    path = "modules/jgit",
)

java_dependencies()

CAFFEINE_GUAVA_SHA256 = "a7ce6d29c40bccd688815a6734070c55b20cd326351a06886a6144005aa32299"

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

load("@build_bazel_rules_nodejs//:index.bzl", "yarn_install")

yarn_install(
    name = "npm",
    frozen_lockfile = False,
    package_json = "//:package.json",
    package_path = "",
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
    frozen_lockfile = False,
    package_json = "//:polygerrit-ui/app/package.json",
    package_path = "polygerrit-ui/app",
    yarn_lock = "//:polygerrit-ui/app/yarn.lock",
)

yarn_install(
    name = "ui_dev_npm",
    frozen_lockfile = False,
    package_json = "//:polygerrit-ui/package.json",
    package_path = "polygerrit-ui",
    yarn_lock = "//:polygerrit-ui/yarn.lock",
)

yarn_install(
    name = "tools_npm",
    frozen_lockfile = False,
    package_json = "//:tools/node_tools/package.json",
    package_path = "tools/node_tools",
    yarn_lock = "//:tools/node_tools/yarn.lock",
)

yarn_install(
    name = "plugins_npm",
    args = ["--prod"],
    frozen_lockfile = False,
    package_json = "//:plugins/package.json",
    package_path = "plugins",
    yarn_lock = "//:plugins/yarn.lock",
)

external_plugin_deps()
