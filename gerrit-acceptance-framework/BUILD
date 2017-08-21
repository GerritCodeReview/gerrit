load("//tools/bzl:java.bzl", "java_library2")

SRCS = glob(["src/test/java/com/google/gerrit/acceptance/*.java"])

PROVIDED = [
    "//java/com/google/gerrit/common:annotations",
    "//java/com/google/gerrit/common:server",
    "//java/com/google/gerrit/extensions:api",
    "//java/com/google/gerrit/index",
    "//gerrit-httpd:httpd",
    "//gerrit-lucene:lucene",
    "//gerrit-pgm:init",
    "//java/com/google/gerrit/reviewdb:server",
    "//gerrit-server:metrics",
    "//gerrit-server:receive",
    "//gerrit-server:server",
    "//lib:gson",
    "//lib:jsch",
    "//lib/jgit/org.eclipse.jgit:jgit",
    "//lib/mina:sshd",
    "//lib:servlet-api-3_1",
]

java_binary(
    name = "acceptance-framework",
    testonly = 1,
    main_class = "Dummy",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_library2(
    name = "lib",
    testonly = 1,
    srcs = SRCS,
    exported_deps = [
        "//gerrit-gpg:gpg",
        "//gerrit-launcher:launcher",
        "//gerrit-openid:openid",
        "//gerrit-pgm:daemon",
        "//gerrit-pgm:http-jetty",
        "//gerrit-pgm:util-nodep",
        "//gerrit-server:testutil",
        "//java/com/google/gerrit/index:query_exception",
        "//lib:jimfs",
        "//lib:truth",
        "//lib:truth-java8-extension",
        "//lib/auto:auto-value",
        "//lib/httpcomponents:fluent-hc",
        "//lib/httpcomponents:httpclient",
        "//lib/httpcomponents:httpcore",
        "//lib/jetty:servlet",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
        "//lib/log:impl_log4j",
        "//lib/log:log4j",
        "//prolog:gerrit-prolog-common",
    ],
    visibility = ["//visibility:public"],
    deps = PROVIDED + [
        # We want these deps to be exported_deps
        "//lib/greenmail:greenmail",
        "//lib:gwtorm",
        "//lib/guice:guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
        "//lib/mail:mail",
    ],
)

load("//tools/bzl:javadoc.bzl", "java_doc")

java_doc(
    name = "acceptance-framework-javadoc",
    testonly = 1,
    libs = [":lib"],
    pkgs = ["com.google.gerrit.acceptance"],
    title = "Gerrit Acceptance Test Framework Documentation",
    visibility = ["//visibility:public"],
)
