load("//tools/bzl:junit.bzl", "junit_tests")

CUSTOM_TRUTH_SUBJECTS = glob([
    "**/*Subject.java",
])

java_library(
    name = "custom-truth-subjects",
    testonly = 1,
    srcs = CUSTOM_TRUTH_SUBJECTS,
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/truth",
        "//lib:truth",
    ],
)

junit_tests(
    name = "server_tests",
    size = "large",
    srcs = glob(
        ["**/*.java"],
        exclude = CUSTOM_TRUTH_SUBJECTS,
    ),
    # Enforce JDK 8 compatibility, see
    # https://docs.oracle.com/javase/9/intl/internationalization-enhancements-jdk-9.htm#JSINT-GUID-AF5AECA7-07C1-4E7D-BC10-BC7E73DC6C7F
    jvm_flags = ["-Djava.locale.providers=COMPAT,CLDR,SPI"],
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/server"],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//lib/bouncycastle:bcprov",
    ],
    deps = [
        ":custom-truth-subjects",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/extensions/common/testing:common-test-util",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/org/eclipse/jgit:server",
        "//lib:grappa",
        "//lib:gson",
        "//lib:guava-retrying",
        "//lib:gwtorm",
        "//lib:truth-java8-extension",
        "//lib/commons:codec",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
