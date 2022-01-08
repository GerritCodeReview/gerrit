load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")
load("//tools/bzl:junit.bzl", "JVM_OPT_ADD_OPENS_JAVA_UTIL_ALL_UNNAMED")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "server_project",
    labels = ["server"],
    vm_args = [JVM_OPT_ADD_OPENS_JAVA_UTIL_ALL_UNNAMED],
    deps = ["//java/com/google/gerrit/mail"],
)
