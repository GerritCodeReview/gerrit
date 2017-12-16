load("//lib/prolog:prolog.bzl", "prolog_cafe_library")

prolog_cafe_library(
    name = "gerrit-prolog-common",
    srcs = ["gerrit_common.pl"],
    visibility = ["//visibility:public"],
    deps = ["//java/gerrit:prolog-predicates"],
)

# TODO(davido): Remove that mess when that major Bazel design bug is fixed
# https://github.com/bazelbuild/bazel/issues/1402
prolog_cafe_library(
    name = "gerrit-prolog-common-neverlink",
    srcs = ["gerrit_common.pl"],
    neverlink = True,
    visibility = ["//visibility:public"],
    deps = ["//java/gerrit:prolog-predicates"],
)
