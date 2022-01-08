load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")
load("//tools/bzl:junit.bzl", "JVM_OPT_ADD_OPENS_JAVA_UTIL_ALL_UNNAMED")

# TODO(davido): Remove additional vm_args when this issue is fixed:
# https://crbug.com/gerrit/15504
[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = [
        "api",
    ],
    vm_args = [JVM_OPT_ADD_OPENS_JAVA_UTIL_ALL_UNNAMED] if f[:f.index(".")] == "SubmitRequirementIT" else [],
    deps = ["//java/com/google/gerrit/server/util/time"],
) for f in glob(["*IT.java"])]
