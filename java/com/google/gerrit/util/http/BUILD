load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("@rules_java//java:defs.bzl", "java_library")

# Canonical EE8/javax sources, shared by the EE8 :http library and the EE10
# transform below. util/http is a servlet-boundary package (CacheHeaders,
# RequestUtil take HttpServletRequest/Response), so it needs an EE10 flavour
# like httpd.
filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

java_library(
    name = "http",
    srcs = [":srcs"],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
    ],
)

# --- EE10 (jakarta.servlet) forward-generated flavour -----------------------
javax_to_jakarta_srcjar(
    name = "http-ee10-srcs",
    direction = "to_jakarta",
    sources = [":srcs"],
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

java_library(
    name = "http-ee10",
    srcs = [":http-ee10-srcs"],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib:jakarta-servlet-api",
        "//lib/errorprone:annotations",
    ],
)
