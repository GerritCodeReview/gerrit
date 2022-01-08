load(
    "//tools/bzl:junit.bzl",
    "JVM_OPT_ADD_OPENS_JAVA_UTIL_ALL_UNNAMED",
    "junit_tests",
)

SR_SERIALIZER_TESTS = [
    "SubmitRequirementExpressionResultSerializerTest.java",
    "SubmitRequirementJsonSerializerTest.java",
    "SubmitRequirementSerializerTest.java",
]

junit_tests(
    name = "tests",
    srcs = glob(
        ["*.java"],
        exclude = SR_SERIALIZER_TESTS,
    ),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/serialize/entities",
        "//java/com/google/gerrit/server/util/time",
        "//lib:jgit",
        "//lib/truth",
    ],
)

# TODO(davido): Remove this rule with jvm_flags, when this issue is fixed:
# https://crbug.com/gerrit/15504
junit_tests(
    name = "sr_serializer_tests",
    srcs = SR_SERIALIZER_TESTS,
    jvm_flags = [JVM_OPT_ADD_OPENS_JAVA_UTIL_ALL_UNNAMED],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/serialize/entities",
        "//lib:gson",
        "//lib:jgit",
        "//lib/truth",
    ],
)
