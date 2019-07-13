load("//tools/bzl:java.bzl", "java_library2")
load("//tools/bzl:javadoc.bzl", "java_doc")

package(default_visibility = ["//visibility:public"])

SRCS = glob(["src/main/java/com/google/gerrit/**/*.java"])

DEPS = ["//lib/gwt:user-neverlink"]

java_binary(
    name = "gwtui-api",
    main_class = "Dummy",
    runtime_deps = [
        ":gwtui-api-lib",
        "//gerrit-gwtui-common:client-lib",
    ],
)

java_library2(
    name = "gwtui-api-lib",
    srcs = SRCS,
    exported_deps = [
        "//gerrit-gwtui:client-lib",
        "//gerrit-gwtui-common:client-lib",
    ],
    resources = glob(["src/main/**/*"]),
    deps = DEPS + [
        "//java/org/eclipse/jgit:libclient-src.jar",
        "//java/org/eclipse/jgit:libEdit-src.jar",
        "//java/com/google/gerrit/common:libclient-src.jar",
        "//java/com/google/gerrit/extensions:libapi-src.jar",
        "//java/com/google/gwtexpui/clippy:libclippy-src.jar",
        "//java/com/google/gwtexpui/globalkey:libglobalkey-src.jar",
        "//java/com/google/gwtexpui/progress:libprogress-src.jar",
        "//java/com/google/gwtexpui/safehtml:libsafehtml-src.jar",
        "//java/com/google/gwtexpui/user:libagent-src.jar",
        "//gerrit-gwtui-common:libclient-src.jar",
        "//java/com/google/gerrit/prettify:libclient-src.jar",
        "//java/com/google/gerrit/reviewdb:libclient-src.jar",
        "//lib/gwt:dev-neverlink",
    ],
)

java_library2(
    name = "gwtui-api-lib-neverlink",
    srcs = SRCS,
    exported_deps = ["//gerrit-gwtui-common:client-lib"],
    neverlink = 1,  # we want this to be exported deps
    resources = glob(["src/main/**/*"]),
    deps = DEPS + ["//lib/gwt:dev"],
)

java_binary(
    name = "gwtui-api-source",
    main_class = "Dummy",
    runtime_deps = [
        ":libgwtui-api-lib-src.jar",
        "//gerrit-gwtui-common:libclient-lib-src.jar",
        "//java/com/google/gwtexpui/clippy:libclippy-src.jar",
        "//java/com/google/gwtexpui/globalkey:libglobalkey-src.jar",
        "//java/com/google/gwtexpui/progress:libprogress-src.jar",
        "//java/com/google/gwtexpui/safehtml:libsafehtml-src.jar",
        "//java/com/google/gwtexpui/user:libagent-src.jar",
    ],
)

java_doc(
    name = "gwtui-api-javadoc",
    libs = DEPS + [
        ":gwtui-api-lib",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm-client",
        "//lib/gwt:dev",
        "//gerrit-gwtui-common:client-lib",
        "//java/com/google/gerrit/common:client",
        "//java/com/google/gerrit/reviewdb:client",
    ],
    pkgs = [
        "com.google.gerrit.plugin",
        "com.google.gwtexpui.clippy",
        "com.google.gwtexpui.globalkey",
        "com.google.gwtexpui.safehtml",
        "com.google.gwtexpui.user",
    ],
    title = "Gerrit Review GWT Extension API Documentation",
)
