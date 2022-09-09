load("@rules_java//java:defs.bzl", "java_library")

ACCOUNT_DATA = [
    "AccountAttribute.java",
]

java_library(
    name = "data",
    srcs = glob(
        ["*.java"],
        exclude = ACCOUNT_DATA,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":account_data",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//lib:gson",
    ],
)

java_library(
    name = "account_data",
    srcs = ACCOUNT_DATA,
    visibility = ["//visibility:public"],
)
