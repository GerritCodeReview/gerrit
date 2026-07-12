load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_java_library", "flavoured_twin_alias")

flavoured_twin_alias(
    name = "testutil",
    default_flavour = "ee11",
    flavours = ["ee8"],
    visibility = ["//visibility:public"],
)

flavoured_java_library(
    name = "testutil-ee11",
    testonly = True,
    srcs = glob(["**/*.java"]),
    flavour = "ee11",
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
        "//lib/httpcomponents:httpclient",
    ],
)

# EE8 (javax.servlet) twin: the FakeHttpServlet{Request,Response} variants
# diverge from a mechanical transform (Servlet-6 method set), so this twin
# exports the hand-written ee8/ overlay instead of running to_javax.
# testutil-ee11's glob does not descend into ee8/ (own BUILD).
flavoured_java_library(
    name = "testutil-ee8",
    testonly = True,
    flavour = "ee8",
    visibility = ["//visibility:public"],
    exports = ["//javatests/com/google/gerrit/util/http/testutil/ee8:overlay"],
)
