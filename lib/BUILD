load("@rules_java//java:defs.bzl", "java_import", "java_library")

exports_files(glob([
    "LICENSE-*",
]))

filegroup(
    name = "all-licenses",
    srcs = glob(
        ["LICENSE-*"],
        exclude = ["LICENSE-DO_NOT_DISTRIBUTE"],
    ),
    visibility = ["//visibility:public"],
)

java_library(
    name = "servlet-api",
    data = ["//lib:LICENSE-Apache2.0"],
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//servlet-api/jar"],
)

java_library(
    name = "servlet-api-without-neverlink",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//servlet-api/jar"],
)

java_library(
    name = "gson",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//gson/jar"],
)

java_library(
    name = "jgit",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit:jgit"],
    runtime_deps = [
        ":javaewah",
        "//lib/log:api",
    ],
)

java_library(
    name = "jgit-ssh-apache",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.ssh.apache:ssh-apache"],
    runtime_deps = [
        "//lib/mina:sshd-sftp",
    ],
)

java_library(
    name = "jgit-archive",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.archive:jgit-archive"],
    runtime_deps = [":jgit"],
)

java_library(
    name = "jgit-junit",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.junit:junit"],
    runtime_deps = [":jgit"],
)

java_library(
    name = "jgit-servlet",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.http.server:jgit-servlet"],
    runtime_deps = [":jgit"],
)

java_library(
    name = "javaewah",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//javaewah/jar"],
)

java_library(
    name = "protobuf",
    data = ["//lib:LICENSE-protobuf"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//protobuf-java/jar"],
)

java_library(
    name = "guava-failureaccess",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//guava-failureaccess/jar"],
)

java_library(
    name = "j2objc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//j2objc/jar"],
)

java_library(
    name = "guava",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guava-failureaccess",
        ":j2objc",
        "@maven_deps//guava/jar",
    ],
)

java_library(
    name = "guava-testlib",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@maven_deps//guava-testlib/jar",
    ],
)

java_library(
    name = "caffeine",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = [
        "//java/com/google/gerrit/acceptance:__pkg__",
        "//java/com/google/gerrit/server/cache/mem:__pkg__",
    ],
    exports = ["@maven_deps//caffeine/jar"],
)

java_import(
    name = "caffeine-guava-renamed",
    jars = ["@caffeine-guava-renamed//file"],
)

java_library(
    name = "caffeine-guava",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = [
        "//java/com/google/gerrit/acceptance:__pkg__",
        "//java/com/google/gerrit/server/cache/mem:__pkg__",
    ],
    exports = [":caffeine-guava-renamed"],
)

java_library(
    name = "args4j",
    data = ["//lib:LICENSE-args4j"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//args4j/jar"],
)

java_library(
    name = "automaton",
    data = ["//lib:LICENSE-automaton"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//automaton/jar"],
)

java_library(
    name = "flexmark-all-lib",
    data = ["//lib:LICENSE-flexmark"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//flexmark-all-lib/jar"],
)

java_library(
    name = "autolink",
    data = ["//lib:LICENSE-autolink"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//autolink/jar"],
)

java_library(
    name = "tukaani-xz",
    data = ["//lib:LICENSE-xz"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//tukaani-xz/jar"],
)

java_library(
    name = "mime-util",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//mime-util/jar"],
)

java_library(
    name = "guava-retrying",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//guava-retrying/jar"],
    runtime_deps = [":jsr305"],
)

java_library(
    name = "jsr305",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//jsr305/jar"],
)

java_library(
    name = "blame-cache",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//blame-cache/jar"],
)

java_library(
    name = "h2",
    data = ["//lib:LICENSE-h2"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//h2/jar"],
)

java_library(
    name = "jimfs",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//jimfs/jar"],
    runtime_deps = [":guava"],
)

java_library(
    name = "junit",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":hamcrest",
        "@maven_deps//junit/jar",
    ],
    runtime_deps = [":hamcrest"],
)

java_library(
    name = "hamcrest",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//hamcrest/jar"],
)

java_library(
    name = "soy",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//soy/jar"],
    runtime_deps = [
        ":args4j",
        ":gson",
        ":guava",
        ":html-types",
        ":icu4j",
        ":jsr305",
        ":protobuf",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:javax_inject",
        "//lib/ow2:ow2-asm",
        "//lib/ow2:ow2-asm-analysis",
        "//lib/ow2:ow2-asm-commons",
        "//lib/ow2:ow2-asm-util",
    ],
)

java_library(
    name = "html-types",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//html-types/jar"],
)

java_library(
    name = "icu4j",
    data = ["//lib:LICENSE-icu4j"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//icu4j/jar"],
)

java_library(
    name = "roaringbitmap",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@maven_deps//roaringbitmap-shims/jar",
        "@maven_deps//roaringbitmap/jar",
    ],
)

sh_test(
    name = "nongoogle_test",
    srcs = ["nongoogle_test.sh"],
    data = ["//tools:nongoogle.bzl"],
)
